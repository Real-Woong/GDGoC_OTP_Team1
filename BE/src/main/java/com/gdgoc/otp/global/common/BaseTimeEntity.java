package com.gdgoc.otp.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 생성 시각과 수정 시각을 공통으로 가지는 엔티티의 부모 클래스.
 *
 * <p>ERD(prisma/schema.prisma)에서 createdAt, updatedAt을 둘 다 가진 테이블
 * (User, Payslip, TaxRuleVersion, TaskTemplate, UserTask)이 이 클래스를 상속한다.
 * CheckFinding은 createdAt만, PayslipField는 시간 컬럼이 없어서 상속하지 않는다.</p>
 *
 * <p>{@code @MappedSuperclass}: 이 클래스 자체는 테이블이 되지 않는다.
 * 대신 상속한 엔티티의 테이블에 created_at, updated_at 컬럼이 들어간다.</p>
 *
 * <p>값은 Hibernate가 넣는다. INSERT할 때 {@code @CreationTimestamp}가 현재 시각을,
 * INSERT·UPDATE할 때 {@code @UpdateTimestamp}가 현재 시각을 채운다.
 * 그래서 서비스 코드에서 직접 시각을 넣을 필요가 없다.</p>
 *
 * <p>타입이 {@link Instant}인 이유: DB 컬럼이 PostgreSQL timestamptz(시간대 포함)이다.
 * Instant는 시간대와 무관한 "절대 시각"이라 서버가 어느 나라에서 돌든 같은 값을 저장한다.
 * 화면에 보여줄 때만 한국 시간(Asia/Seoul)으로 바꾼다.</p>
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity {

    /** 행이 처음 저장된 시각. 한 번 저장되면 바뀌지 않도록 updatable = false. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 행이 마지막으로 수정된 시각. 엔티티 필드가 바뀌어 UPDATE가 나갈 때마다 갱신된다. */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
