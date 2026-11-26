package com.gdgoc.otp.task.dto;

import com.gdgoc.otp.task.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 할 일 상태 변경 요청.
 *
 * <pre>
 * PATCH /api/tasks/3/status
 * { "status": "DONE" }        // 완료
 * { "status": "NOT_APPLICABLE" } // 해당 없음
 * { "status": "TODO" }        // 되돌리기
 * </pre>
 *
 * <p>enum 이름이 아닌 값("FINISHED" 등)을 보내면 JSON 변환 단계에서 실패해 400이 난다.</p>
 */
public record UpdateTaskStatusRequest(
        @NotNull(message = "상태는 필수입니다.")
        TaskStatus status
) {
}
