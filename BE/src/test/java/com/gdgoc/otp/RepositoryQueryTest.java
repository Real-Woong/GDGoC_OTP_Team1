package com.gdgoc.otp;

import com.gdgoc.otp.finding.domain.CheckFinding;
import com.gdgoc.otp.finding.domain.Severity;
import com.gdgoc.otp.finding.repository.CheckFindingRepository;
import com.gdgoc.otp.payslip.domain.Payslip;
import com.gdgoc.otp.payslip.domain.PayslipField;
import com.gdgoc.otp.payslip.domain.PayslipFieldKey;
import com.gdgoc.otp.payslip.repository.PayslipRepository;
import com.gdgoc.otp.support.IntegrationTestSupport;
import com.gdgoc.otp.task.domain.TaskTemplate;
import com.gdgoc.otp.task.domain.UserTask;
import com.gdgoc.otp.task.repository.TaskTemplateRepository;
import com.gdgoc.otp.task.repository.UserTaskRepository;
import com.gdgoc.otp.taxrule.domain.RuleStatus;
import com.gdgoc.otp.taxrule.repository.TaxRuleVersionRepository;
import com.gdgoc.otp.user.domain.User;
import com.gdgoc.otp.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository의 메서드 이름 쿼리가 의도대로 동작하는지 확인한다.
 * 특히 "남의 데이터는 조회되지 않는다"(소유권 조건)를 확인하는 것이 핵심이다.
 */
@Transactional
class RepositoryQueryTest extends IntegrationTestSupport {

    @Autowired UserRepository userRepository;
    @Autowired PayslipRepository payslipRepository;
    @Autowired TaxRuleVersionRepository taxRuleVersionRepository;
    @Autowired CheckFindingRepository checkFindingRepository;
    @Autowired TaskTemplateRepository taskTemplateRepository;
    @Autowired UserTaskRepository userTaskRepository;
    @Autowired EntityManager em;

    @Test
    void authSubject로_사용자를_찾는다() {
        userRepository.save(User.create("google-123"));

        assertThat(userRepository.findByAuthSubject("google-123")).isPresent();
        assertThat(userRepository.findByAuthSubject("없는-사용자")).isEmpty();
    }

    @Test
    void 남의_명세서는_조회되지_않는다() {
        User me = userRepository.save(User.create("me"));
        User other = userRepository.save(User.create("other"));
        Payslip mine = Payslip.create(me, "2026-03", null, null, null);
        mine.addField(PayslipField.fromOcr(PayslipFieldKey.BASE_SALARY, 2_500_000, 0.95, "기본급"));
        payslipRepository.save(mine);
        em.flush();
        em.clear();

        assertThat(payslipRepository.findWithFieldsByIdAndUser_Id(mine.getId(), me.getId())).isPresent();
        assertThat(payslipRepository.findWithFieldsByIdAndUser_Id(mine.getId(), other.getId())).isEmpty();
    }

    @Test
    void 명세서_상세조회는_필드를_함께_가져온다() {
        User me = userRepository.save(User.create("me2"));
        Payslip payslip = Payslip.create(me, "2026-03", null, null, null);
        payslip.addField(PayslipField.fromOcr(PayslipFieldKey.BASE_SALARY, 2_500_000, 0.95, "기본급"));
        payslipRepository.save(payslip);
        em.flush();
        em.clear();

        Payslip found = payslipRepository.findWithFieldsByIdAndUser_Id(payslip.getId(), me.getId()).orElseThrow();
        // @EntityGraph 덕분에 fields가 이미 로딩되어 있다(추가 쿼리 없음)
        assertThat(Hibernate.isInitialized(found.getFields())).isTrue();
    }

    @Test
    void 명세서_목록은_최근_귀속월이_먼저() {
        User me = userRepository.save(User.create("me3"));
        payslipRepository.save(Payslip.create(me, "2026-01", null, null, null));
        payslipRepository.save(Payslip.create(me, "2026-03", null, null, null));
        payslipRepository.save(Payslip.create(me, "2025-12", null, null, null));

        assertThat(payslipRepository.findByUser_IdOrderByPayPeriodDesc(me.getId()))
                .extracting(Payslip::getPayPeriod)
                .containsExactly("2026-03", "2026-01", "2025-12");
        assertThat(payslipRepository.existsByUser_IdAndPayPeriod(me.getId(), "2026-03")).isTrue();
        assertThat(payslipRepository.existsByUser_IdAndPayPeriod(me.getId(), "2026-02")).isFalse();
    }

    @Test
    void 연도별_최신_규칙을_찾는다() {
        assertThat(taxRuleVersionRepository.findTopByTaxYearOrderByIdDesc(2026)).isPresent();
        assertThat(taxRuleVersionRepository.findTopByTaxYearAndStatusOrderByIdDesc(2026, RuleStatus.VERIFIED))
                .as("시드 규칙은 DRAFT뿐이라 VERIFIED는 없다").isEmpty();
        assertThat(taxRuleVersionRepository.findTopByTaxYearOrderByIdDesc(2099)).isEmpty();
    }

    @Test
    void 할일_목록은_기한순이고_기한없는것은_맨뒤() {
        User me = userRepository.save(User.create("me4"));
        TaskTemplate sme = taskTemplateRepository.findByKey("sme-reduction-application").orElseThrow();
        TaskTemplate dependent = taskTemplateRepository.findByKey("dependent-registration").orElseThrow();
        UserTask noDue = userTaskRepository.save(UserTask.assign(me, dependent, null, null));
        UserTask withDue = userTaskRepository.save(UserTask.assign(me, sme, null, LocalDate.of(2026, 12, 31)));
        em.flush();
        em.clear();

        List<UserTask> tasks = userTaskRepository.findByUser_IdOrderByDueOnAscIdAsc(me.getId());
        assertThat(tasks).extracting(UserTask::getId).containsExactly(withDue.getId(), noDue.getId());
        assertThat(Hibernate.isInitialized(tasks.get(0).getTemplate())).isTrue();

        assertThat(userTaskRepository.existsByUser_IdAndTemplate_Id(me.getId(), sme.getId())).isTrue();
        assertThat(userTaskRepository.findByIdAndUser_Id(withDue.getId(), me.getId() + 999)).isEmpty();
    }

    @Test
    void 명세서별_점검결과를_만든_순서로_가져온다() {
        User me = userRepository.save(User.create("me5"));
        Payslip payslip = payslipRepository.save(Payslip.create(me, "2026-03", null, null, null));
        var rule = taxRuleVersionRepository.findTopByTaxYearOrderByIdDesc(2026).orElseThrow();
        checkFindingRepository.save(CheckFinding.create(payslip, rule, "A", Severity.INFO, "첫 번째"));
        checkFindingRepository.save(CheckFinding.create(payslip, rule, "B", Severity.WARNING, "두 번째"));

        assertThat(checkFindingRepository.findByPayslip_IdOrderByIdAsc(payslip.getId()))
                .extracting(CheckFinding::getRuleCode)
                .containsExactly("A", "B");
    }
}
