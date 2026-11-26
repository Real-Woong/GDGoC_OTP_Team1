package com.gdgoc.otp.task.domain;

import com.gdgoc.otp.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 할 일의 "원본(틀)". ERD의 TaskTemplate 테이블.
 *
 * <p>예: "회사에 중소기업 취업자 소득세 감면 신청서 제출", "연말정산 부양가족 정보 제출".
 * 모든 사용자에게 공통인 내용은 여기 한 번만 저장하고,
 * 사용자별 진행 상태와 실제 기한은 {@link UserTask}에 저장한다.</p>
 *
 * <p>운영자가 시드 데이터(Flyway 마이그레이션)로 넣는 값이라 생성 메서드만 두었다.</p>
 */
@Getter
@Entity
@Table(name = "task_template")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 프로그램에서 템플릿을 찾을 때 쓰는 고유 키. 예: "SME_TAX_REDUCTION_APPLICATION"
     * id는 환경(로컬/서버)마다 달라질 수 있어서, 코드에서는 key로 찾는다.
     */
    @Column(name = "key", nullable = false, unique = true, length = 100)
    private String key;

    /** 화면에 보여줄 할 일 제목. */
    @Column(name = "title", nullable = false)
    private String title;

    /** 자세한 설명(무엇을, 어디에, 왜). */
    @Column(name = "description", columnDefinition = "text")
    private String description;

    /**
     * 상대 기한 규칙. 예: "END_OF_MONTH_AFTER_JOIN"(입사한 달의 다음 달 말일)
     * 사용자 입사일 같은 기준일에 이 규칙을 적용해 UserTask.dueOn을 계산한다.
     * 형식은 아직 팀 결정 전이라 문자열로 둔다. 형식이 정해지면 enum으로 바꾸는 것을 권한다.
     */
    @Column(name = "due_rule", nullable = false, length = 100)
    private String dueRule;

    public static TaskTemplate create(String key, String title, String description, String dueRule) {
        TaskTemplate template = new TaskTemplate();
        template.key = key;
        template.title = title;
        template.description = description;
        template.dueRule = dueRule;
        return template;
    }
}
