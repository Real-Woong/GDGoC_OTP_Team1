package com.gdgoc.otp.global.crypto;

import com.gdgoc.otp.payslip.domain.Payslip;
import com.gdgoc.otp.payslip.domain.PayslipField;
import com.gdgoc.otp.payslip.domain.PayslipFieldKey;
import com.gdgoc.otp.payslip.domain.PayslipWarning;
import com.gdgoc.otp.payslip.domain.UnmappedItem;
import com.gdgoc.otp.support.IntegrationTestSupport;
import com.gdgoc.otp.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 명세서 데이터가 DB에 "정말로" 암호화되어 들어가는지 확인한다.
 *
 * <p>JPA로 읽으면 컨버터가 자동 복호화하므로 암호화 여부를 알 수 없다. 그래서 네이티브 SQL로
 * DB에 실제로 저장된 문자열을 꺼내 "v1:"로 시작하고 평문이 보이지 않는지 본다.
 * DB가 유출됐을 때 공격자가 보게 되는 모습이 바로 이것이다.</p>
 */
@Transactional
class EncryptedStorageTest extends IntegrationTestSupport {

    @Autowired EntityManager em;

    private Long savePayslip() {
        User user = User.create("crypto-test");
        em.persist(user);
        Payslip payslip = Payslip.create(user, "2026-03", "샘플주식회사",
                List.of(new UnmappedItem("직책수당", 100_000)),
                List.of(new PayslipWarning("net_pay", "실수령액 2,543,430원을 확인해 주세요.")));
        payslip.addField(PayslipField.fromOcr(PayslipFieldKey.BASE_SALARY, 2_400_000, 0.98, "기본급"));
        payslip.addField(PayslipField.fromOcr(PayslipFieldKey.BONUS, null, null, null));
        em.persist(payslip);
        em.flush();
        em.clear(); // 1차 캐시를 비워, 다음 조회가 DB에서 다시 읽게 한다
        return payslip.getId();
    }

    private String raw(String sql, Long id) {
        Object value = em.createNativeQuery(sql).setParameter("id", id).getSingleResult();
        return value == null ? null : value.toString();
    }

    @Test
    void DB에는_암호문만_저장된다() {
        Long id = savePayslip();

        String value = raw("select f.value from payslip_field f where f.payslip_id = :id and f.key = 'BASE_SALARY'", id);
        String label = raw("select f.source_label from payslip_field f where f.payslip_id = :id and f.key = 'BASE_SALARY'", id);
        String company = raw("select p.company_name from payslip p where p.id = :id", id);
        String unmapped = raw("select p.unmapped_items from payslip p where p.id = :id", id);
        String warnings = raw("select p.warnings from payslip p where p.id = :id", id);

        assertThat(List.of(value, label, company, unmapped, warnings)).allSatisfy(s -> assertThat(s).startsWith("v1:"));
        assertThat(value).doesNotContain("2400000");
        assertThat(label).doesNotContain("기본급");
        assertThat(company).doesNotContain("샘플");
        assertThat(unmapped).doesNotContain("직책수당").doesNotContain("100000");
        assertThat(warnings).doesNotContain("2,543,430");
    }

    @Test
    void 못읽은_값_null은_null로_남는다() {
        Long id = savePayslip();
        // "모름"과 0원을 구분해야 하므로 null을 암호화하지 않는다.
        assertThat(raw("select f.value from payslip_field f where f.payslip_id = :id and f.key = 'BONUS'", id)).isNull();
    }

    @Test
    void JPA로_읽으면_자동으로_복호화된다() {
        Long id = savePayslip();

        Payslip found = em.find(Payslip.class, id);
        assertThat(found.getCompanyName()).isEqualTo("샘플주식회사");
        assertThat(found.getUnmappedItems()).containsExactly(new UnmappedItem("직책수당", 100_000));
        assertThat(found.getWarnings()).containsExactly(new PayslipWarning("net_pay", "실수령액 2,543,430원을 확인해 주세요."));
        PayslipField base = found.findField(PayslipFieldKey.BASE_SALARY).orElseThrow();
        assertThat(base.getValue()).isEqualTo(2_400_000);
        assertThat(base.getSourceLabel()).isEqualTo("기본급");
    }

    @Test
    void 암호화_도입_전_평문_행도_읽힌다() {
        Long id = savePayslip();
        // V3 이전에 저장된 데이터처럼 평문을 직접 넣어 본다.
        em.createNativeQuery("update payslip_field set value = '2500000', source_label = '기본급여' "
                + "where payslip_id = :id and key = 'BASE_SALARY'").setParameter("id", id).executeUpdate();
        em.createNativeQuery("update payslip set unmapped_items = '[{\"label\":\"식대\",\"amount\":1}]' where id = :id")
                .setParameter("id", id).executeUpdate();
        em.clear();

        Payslip found = em.find(Payslip.class, id);
        assertThat(found.findField(PayslipFieldKey.BASE_SALARY).orElseThrow().getValue()).isEqualTo(2_500_000);
        assertThat(found.getUnmappedItems()).containsExactly(new UnmappedItem("식대", 1));
    }
}
