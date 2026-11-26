package com.gdgoc.otp;

import com.gdgoc.otp.finding.domain.CheckFinding;
import com.gdgoc.otp.finding.domain.Severity;
import com.gdgoc.otp.payslip.domain.FieldStatus;
import com.gdgoc.otp.payslip.domain.Payslip;
import com.gdgoc.otp.payslip.domain.PayslipField;
import com.gdgoc.otp.payslip.domain.PayslipFieldKey;
import com.gdgoc.otp.payslip.domain.PayslipWarning;
import com.gdgoc.otp.payslip.domain.UnmappedItem;
import com.gdgoc.otp.support.IntegrationTestSupport;
import com.gdgoc.otp.task.domain.TaskStatus;
import com.gdgoc.otp.task.domain.TaskTemplate;
import com.gdgoc.otp.task.domain.UserTask;
import com.gdgoc.otp.taxrule.domain.RuleStatus;
import com.gdgoc.otp.taxrule.domain.TaxRuleVersion;
import com.gdgoc.otp.user.domain.User;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 엔티티가 실제 PostgreSQL 테이블에 제대로 저장되고 다시 읽히는지 확인한다.
 *
 * <p>{@code @Transactional}: 테스트가 끝나면 롤백되어 DB에 데이터가 남지 않는다.
 * 테스트끼리 서로의 데이터에 영향을 주지 않게 하기 위해서다.</p>
 *
 * <p>{@code em.flush(); em.clear();} 패턴: flush로 지금까지의 변경을 SQL로 DB에 보내고,
 * clear로 JPA가 메모리에 들고 있던 객체를 비운다. 그 뒤 find하면 메모리가 아니라
 * DB에서 새로 읽어오므로 "진짜로 저장됐는지"를 확인할 수 있다.</p>
 */
@Transactional
class EntityMappingTest extends IntegrationTestSupport {

    @Autowired
    EntityManager em;

    @Test
    void 시드_데이터가_들어가_있다() {
        TaxRuleVersion rule = findSeedRule();
        assertThat(rule.getStatus()).isEqualTo(RuleStatus.DRAFT);
        // parameters(jsonb)가 Map으로 읽히는지
        assertThat(rule.getParameters()).containsEntry("schemaVersion", "demo-v1");

        List<TaskTemplate> templates = em.createQuery("select t from TaskTemplate t", TaskTemplate.class).getResultList();
        assertThat(templates).extracting(TaskTemplate::getKey)
                .containsExactlyInAnyOrder("sme-reduction-application", "dependent-registration");
    }

    @Test
    void 명세서와_필드_JSON컬럼이_저장되고_다시_읽힌다() {
        User user = User.create("test-subject-1");
        em.persist(user);

        Payslip payslip = Payslip.create(user, "2026-03", "테스트회사",
                List.of(new UnmappedItem("명절 상여", 300_000)),
                List.of(new PayslipWarning("payment_total", "지급 항목 합계와 1,000원 차이")));
        payslip.addField(PayslipField.fromOcr(PayslipFieldKey.BASE_SALARY, 2_500_000, 0.97, "기본급"));
        payslip.addField(PayslipField.fromOcr(PayslipFieldKey.MEAL_ALLOWANCE, 200_000, 0.5, "중식대"));
        payslip.addField(PayslipField.fromOcr(PayslipFieldKey.BONUS, null, null, null));
        em.persist(payslip); // cascade로 필드 3개도 같이 저장된다
        em.flush();
        em.clear();

        Payslip found = em.find(Payslip.class, payslip.getId());
        assertThat(found.getUnmappedItems()).containsExactly(new UnmappedItem("명절 상여", 300_000));
        assertThat(found.getWarnings()).hasSize(1);
        assertThat(found.getCreatedAt()).isNotNull();

        // 신뢰도로 상태가 자동으로 정해졌는지
        assertThat(found.findField(PayslipFieldKey.BASE_SALARY).orElseThrow().getStatus()).isEqualTo(FieldStatus.OK);
        assertThat(found.findField(PayslipFieldKey.MEAL_ALLOWANCE).orElseThrow().getStatus()).isEqualTo(FieldStatus.NEEDS_REVIEW);
        assertThat(found.findField(PayslipFieldKey.BONUS).orElseThrow().getStatus()).isEqualTo(FieldStatus.MISSING);

        // 아직 아무것도 확인하지 않았으므로 규칙 엔진에 넘길 필드가 없다
        assertThat(found.confirmedFields()).isEmpty();
    }

    @Test
    void 점검결과와_할일이_연결된다() {
        User user = User.create("test-subject-2");
        em.persist(user);
        Payslip payslip = Payslip.create(user, "2026-04", null, null, null);
        em.persist(payslip);

        CheckFinding finding = CheckFinding.create(payslip, findSeedRule(),
                "SME_TAX_REDUCTION", Severity.WARNING, "감면 대상일 수 있습니다.");
        em.persist(finding);

        TaskTemplate template = em.createQuery("select t from TaskTemplate t where t.key = :key", TaskTemplate.class)
                .setParameter("key", "sme-reduction-application")
                .getSingleResult();
        UserTask task = UserTask.assign(user, template, finding, LocalDate.of(2026, 5, 31));
        em.persist(task);
        em.flush();
        em.clear();

        CheckFinding foundFinding = em.find(CheckFinding.class, finding.getId());
        assertThat(foundFinding.isEstimate()).isTrue(); // 항상 예상치

        UserTask foundTask = em.find(UserTask.class, task.getId());
        assertThat(foundTask.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(foundTask.getFinding().getId()).isEqualTo(finding.getId());
        assertThat(foundTask.isOverdue(LocalDate.of(2026, 6, 1))).isTrue();
    }

    @Test
    void 같은_사용자에게_같은_할일을_두번_줄_수_없다() {
        User user = User.create("test-subject-3");
        em.persist(user);
        TaskTemplate template = em.createQuery("select t from TaskTemplate t", TaskTemplate.class)
                .setMaxResults(1).getSingleResult();

        em.persist(UserTask.assign(user, template, null, null));

        // DB의 unique 제약(uk_user_task_user_template)이 막는다.
        // ID가 IDENTITY(DB 자동 증가)면 persist 순간 INSERT가 바로 나가므로 예외도 persist에서 난다.
        assertThatThrownBy(() -> em.persist(UserTask.assign(user, template, null, null)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("uk_user_task_user_template");
    }

    @Test
    void 귀속월_형식이_틀리면_DB가_거부한다() {
        User user = User.create("test-subject-4");
        em.persist(user);
        // 13월은 없다 → pay_period CHECK 제약 위반
        assertThatThrownBy(() -> em.persist(Payslip.create(user, "2026-13", null, null, null)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void 사용자가_확인한_필드만_점검_대상이_된다() {
        Payslip payslip = Payslip.create(User.create("x"), "2026-03", null, null, null);
        payslip.addField(PayslipField.fromOcr(PayslipFieldKey.BASE_SALARY, 2_500_000, 0.97, "기본급"));
        payslip.addField(PayslipField.fromOcr(PayslipFieldKey.MEAL_ALLOWANCE, 20_000, 0.5, "중식대"));

        // 사용자가 식대를 200,000원으로 고쳐서 확인
        PayslipField meal = payslip.findField(PayslipFieldKey.MEAL_ALLOWANCE).orElseThrow();
        meal.confirm(200_000);

        assertThat(payslip.confirmedFields()).containsExactly(meal);
        assertThat(meal.getValue()).isEqualTo(200_000);
        assertThat(meal.getStatus()).isEqualTo(FieldStatus.OK);
        assertThat(meal.getConfidence()).isNull(); // 사람이 고친 값이라 OCR 신뢰도는 지운다
        assertThat(payslip.isFullyConfirmed()).isFalse();
    }

    private TaxRuleVersion findSeedRule() {
        return em.createQuery("select r from TaxRuleVersion r where r.version = :v", TaxRuleVersion.class)
                .setParameter("v", "2026-demo-v1")
                .getSingleResult();
    }
}
