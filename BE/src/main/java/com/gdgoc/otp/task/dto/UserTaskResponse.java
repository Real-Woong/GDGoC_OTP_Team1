package com.gdgoc.otp.task.dto;

import com.gdgoc.otp.task.domain.TaskStatus;
import com.gdgoc.otp.task.domain.UserTask;

import java.time.LocalDate;

/**
 * 할 일 1건 응답. 템플릿의 제목·설명을 합쳐서 화면에 필요한 값을 한 번에 준다.
 *
 * @param templateKey 템플릿 키. 프론트가 아이콘·링크 등을 다르게 보여줄 때 쓸 수 있다
 * @param overdue     기한이 지났는데 아직 안 했는지(오늘 = 한국 날짜 기준)
 * @param findingId   이 할 일을 만든 점검 결과 id. 일반 입사 후 할 일이면 null
 */
public record UserTaskResponse(
        Long id,
        String templateKey,
        String title,
        String description,
        TaskStatus status,
        LocalDate dueOn,
        boolean overdue,
        Long findingId
) {
    public static UserTaskResponse from(UserTask task, LocalDate today) {
        return new UserTaskResponse(
                task.getId(),
                task.getTemplate().getKey(),
                task.getTemplate().getTitle(),
                task.getTemplate().getDescription(),
                task.getStatus(),
                task.getDueOn(),
                task.isOverdue(today),
                // LAZY 프록시에서 getId()는 DB 조회 없이 id만 꺼낸다.
                task.getFinding() == null ? null : task.getFinding().getId()
        );
    }
}
