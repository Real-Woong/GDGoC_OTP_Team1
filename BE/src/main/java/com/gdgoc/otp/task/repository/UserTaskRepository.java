package com.gdgoc.otp.task.repository;

import com.gdgoc.otp.task.domain.UserTask;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * UserTask 테이블 접근.
 */
public interface UserTaskRepository extends JpaRepository<UserTask, Long> {

    /**
     * 내 할 일 목록. 기한이 가까운 순서, 기한이 없는(null) 할 일은 맨 뒤.
     *
     * <p>할 일 화면은 템플릿의 제목·설명을 항상 보여주므로 template을 함께 가져온다.
     * 이게 없으면 할 일 N개를 그릴 때 템플릿 조회 쿼리가 N번 더 나간다(N+1 문제).</p>
     *
     * <p>PostgreSQL은 오름차순 정렬에서 null을 맨 뒤에 둔다.</p>
     */
    @EntityGraph(attributePaths = "template")
    List<UserTask> findByUser_IdOrderByDueOnAscIdAsc(Long userId);

    /** 내 할 일 1건. 소유권 확인을 겸한다. */
    Optional<UserTask> findByIdAndUser_Id(Long id, Long userId);

    /**
     * 이 사용자가 이미 이 템플릿의 할 일을 받았는지.
     * DB unique 제약이 최종 방어선이지만, 미리 확인하면 예외 없이 건너뛸 수 있다.
     */
    boolean existsByUser_IdAndTemplate_Id(Long userId, Long templateId);
}
