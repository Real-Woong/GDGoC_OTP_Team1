package com.gdgoc.otp.task.domain;

import com.gdgoc.otp.finding.domain.CheckFinding;
import com.gdgoc.otp.global.common.BaseTimeEntity;
import com.gdgoc.otp.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 사용자 한 명의 할 일 1건. ERD의 UserTask 테이블.
 *
 * <p>TaskTemplate(공통 틀)을 사용자에게 "발급"한 것이다. 진행 상태와 실제 기한 날짜를 가진다.</p>
 *
 * <p>생기는 경로 두 가지:</p>
 * <ol>
 *   <li>점검 결과에서: 예) 감면 대상 가능성 발견 → "감면 신청서 제출" 할 일 생성. finding이 연결된다.</li>
 *   <li>일반 입사 후 할 일: 예) 모든 신입에게 "부양가족 정보 제출". finding은 null.</li>
 * </ol>
 *
 * <p>같은 사용자는 같은 템플릿을 한 번만 받는다(unique 제약). 명세서를 여러 장 올려
 * 같은 점검 결과가 반복돼도 할 일이 중복으로 쌓이지 않게 하기 위해서다.</p>
 */
@Getter
@Entity
@Table(
        name = "user_task",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_task_user_template", columnNames = {"user_id", "template_id"}),
        indexes = @Index(name = "idx_user_task_finding", columnList = "finding_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTask extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private TaskTemplate template;

    /**
     * 이 할 일을 만든 점검 결과. 일반 입사 후 할 일이면 null.
     * 점검 결과가 지워지면 DB가 이 값을 null로 바꾼다(ON DELETE SET NULL, 마이그레이션에서 설정).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finding_id")
    private CheckFinding finding;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status = TaskStatus.TODO;

    /** 실제 기한 날짜. 템플릿의 dueRule을 기준일에 적용해 계산한다. 기준일(입사일 등)을 모르면 null. */
    @Column(name = "due_on")
    private LocalDate dueOn;

    /**
     * 할 일을 발급한다.
     *
     * @param finding 점검 결과에서 생긴 할 일이면 그 결과, 아니면 null
     * @param dueOn   계산한 기한. 계산할 수 없으면 null
     */
    public static UserTask assign(User user, TaskTemplate template, CheckFinding finding, LocalDate dueOn) {
        UserTask task = new UserTask();
        task.user = user;
        task.template = template;
        task.finding = finding;
        task.dueOn = dueOn;
        return task;
    }

    /** 완료 처리. */
    public void complete() {
        this.status = TaskStatus.DONE;
    }

    /** 나에게 해당 없음 처리. */
    public void markNotApplicable() {
        this.status = TaskStatus.NOT_APPLICABLE;
    }

    /** 실수로 누른 완료/해당없음을 되돌린다. */
    public void reopen() {
        this.status = TaskStatus.TODO;
    }

    /**
     * 기한이 지났는데 아직 안 했는지.
     *
     * @param today 오늘 날짜. 테스트에서 날짜를 고정할 수 있도록 밖에서 받는다.
     */
    public boolean isOverdue(LocalDate today) {
        return status == TaskStatus.TODO && dueOn != null && dueOn.isBefore(today);
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
