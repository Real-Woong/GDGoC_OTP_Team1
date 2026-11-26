package com.gdgoc.otp.task.controller;

import com.gdgoc.otp.global.auth.CurrentUser;
import com.gdgoc.otp.global.auth.LoginUser;
import com.gdgoc.otp.task.dto.UpdateTaskStatusRequest;
import com.gdgoc.otp.task.dto.UserTaskResponse;
import com.gdgoc.otp.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 할 일 API.
 * <ul>
 *   <li>GET   /api/tasks                  내 할 일 목록</li>
 *   <li>PATCH /api/tasks/{id}/status      완료 / 해당 없음 / 되돌리기</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public List<UserTaskResponse> getMyTasks(@CurrentUser LoginUser loginUser) {
        return taskService.getMyTasks(loginUser.id());
    }

    @PatchMapping("/{taskId}/status")
    public UserTaskResponse updateStatus(@CurrentUser LoginUser loginUser,
                                         @PathVariable Long taskId,
                                         @Valid @RequestBody UpdateTaskStatusRequest request) {
        return taskService.updateStatus(loginUser.id(), taskId, request);
    }
}
