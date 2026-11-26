package com.gdgoc.otp.payslip.domain;

import com.gdgoc.otp.global.common.BaseTimeEntity;
import com.gdgoc.otp.user.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 사용자가 업로드한 급여명세서 1건. ERD의 Payslip 테이블.
 *
 * <p>중요: 명세서 원본 파일(이미지/PDF)은 저장하지 않는다. OCR로 항목을 뽑은 직후 원본을 지우고,
 * 뽑아낸 금액만 {@link PayslipField} 행들로 저장한다. 개인정보 노출 범위를 줄이기 위해서다.</p>
 *
 * <p>관계:</p>
 * <ul>
 *   <li>User 1 : N Payslip — 한 사용자가 여러 달의 명세서를 올린다.</li>
 *   <li>Payslip 1 : N PayslipField — 명세서 한 장에 표준 필드가 최대 14칸.</li>
 *   <li>Payslip 1 : N CheckFinding — CheckFinding 쪽에서만 Payslip을 가리킨다(단방향).</li>
 * </ul>
 */
@Getter
@Entity
@Table(
        name = "payslip",
        // "이 사용자의 2026-03 명세서"처럼 사용자+귀속월로 자주 찾으므로 복합 인덱스를 둔다.
        indexes = @Index(name = "idx_payslip_user_pay_period", columnList = "user_id, pay_period")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payslip extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 명세서 주인.
     *
     * <p>{@code fetch = LAZY}: Payslip을 조회할 때 User를 바로 같이 읽지 않고,
     * user 필드를 실제로 사용하는 순간에 읽는다. 기본값(EAGER)은 필요 없는 조인과
     * N+1 쿼리 문제를 일으키기 쉬워서, ManyToOne은 항상 LAZY로 둔다.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 귀속 월. "YYYY-MM" 형식 문자열. 예: "2026-03"
     * 지급일이 아니라 "몇 월 근무분 월급인지"다. 형식 검사는 요청 DTO에서 한다.
     */
    @Column(name = "pay_period", nullable = false, length = 7)
    private String payPeriod;

    /** 명세서에 적힌 회사명. 못 읽었으면 null. */
    @Column(name = "company_name")
    private String companyName;

    /**
     * 표준 필드 14개에 맞추지 못한 원문 항목들. DB에는 JSON 배열로 저장된다.
     *
     * <p>{@code @JdbcTypeCode(SqlTypes.JSON)}: Hibernate가 List를 JSON 문자열로 바꿔
     * PostgreSQL jsonb 컬럼에 넣고, 읽을 때 다시 List로 바꿔준다.
     * 별도 테이블을 만들 만큼 자주 검색하는 값이 아니라서 JSON으로 묶었다.</p>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unmapped_items", nullable = false, columnDefinition = "jsonb")
    private List<UnmappedItem> unmappedItems = new ArrayList<>();

    /** OCR 직후 검증에서 나온 경고들(합계 불일치 등). JSON 배열. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", nullable = false, columnDefinition = "jsonb")
    private List<PayslipWarning> warnings = new ArrayList<>();

    /**
     * 이 명세서의 표준 필드들.
     *
     * <p>{@code mappedBy = "payslip"}: 외래키(payslip_id)는 PayslipField 테이블에 있고,
     * 이 리스트는 "읽기용 반대편"이라는 뜻이다.</p>
     * <p>{@code cascade = ALL}: Payslip을 저장·삭제하면 필드들도 같이 저장·삭제된다.
     * 필드는 명세서 없이 존재할 수 없으므로 생명주기를 함께한다.</p>
     * <p>{@code orphanRemoval = true}: 리스트에서 빼낸 필드는 DB에서도 지운다.</p>
     */
    @OneToMany(mappedBy = "payslip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PayslipField> fields = new ArrayList<>();

    /**
     * 새 명세서를 만든다. OCR 결과를 받은 뒤 호출한다.
     * 필드는 {@link #addField}로 하나씩 붙인다.
     */
    public static Payslip create(User user, String payPeriod, String companyName,
                                 List<UnmappedItem> unmappedItems, List<PayslipWarning> warnings) {
        Payslip payslip = new Payslip();
        payslip.user = user;
        payslip.payPeriod = payPeriod;
        payslip.companyName = companyName;
        // null이 들어와도 DB의 NOT NULL 제약을 지키도록 빈 리스트로 바꾼다.
        payslip.unmappedItems = unmappedItems == null ? new ArrayList<>() : new ArrayList<>(unmappedItems);
        payslip.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
        return payslip;
    }

    /**
     * 필드를 추가한다. 양방향 관계의 양쪽(리스트와 field.payslip)을 함께 맞추는 것이 핵심이다.
     * 한쪽만 맞추면 DB에는 저장되는데 메모리의 객체는 서로를 모르는 상태가 된다.
     *
     * @throws IllegalStateException 같은 key의 필드가 이미 있을 때 (DB의 (payslip_id, key) unique 제약과 같은 규칙)
     */
    public void addField(PayslipField field) {
        if (findField(field.getKey()).isPresent()) {
            throw new IllegalStateException("이미 등록된 필드입니다: " + field.getKey().getCode());
        }
        fields.add(field);
        field.assignPayslip(this);
    }

    /** key로 필드를 찾는다. 없으면 빈 Optional. */
    public Optional<PayslipField> findField(PayslipFieldKey key) {
        return fields.stream().filter(f -> f.getKey() == key).findFirst();
    }

    /**
     * 사용자가 확인을 마친 필드만 돌려준다.
     * 규칙 엔진에는 이 목록만 넘긴다. OCR이 잘못 읽은 값으로 점검하면 잘못된 안내가 나가기 때문이다.
     */
    public List<PayslipField> confirmedFields() {
        return fields.stream().filter(PayslipField::isUserConfirmed).toList();
    }

    /** 모든 필드를 사용자가 확인했는지. "점검하기" 버튼 활성화 조건으로 쓸 수 있다. */
    public boolean isFullyConfirmed() {
        return !fields.isEmpty() && fields.stream().allMatch(PayslipField::isUserConfirmed);
    }

    /** 현재 사용자가 이 명세서의 주인인지. 다른 사람 명세서 조회를 막는 데 쓴다. */
    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
