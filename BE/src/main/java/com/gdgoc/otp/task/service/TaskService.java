package com.gdgoc.otp.task.service;

import com.gdgoc.otp.global.exception.BusinessException;
import com.gdgoc.otp.global.exception.ErrorCode;
import com.gdgoc.otp.task.domain.UserTask;
import com.gdgoc.otp.task.dto.UpdateTaskStatusRequest;
import com.gdgoc.otp.task.dto.UserTaskResponse;
import com.gdgoc.otp.task.repository.UserTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 할 일 조회와 상태 변경.
 *
 * <p>할 일을 "만드는" 기능은 5단계(점검)에서 추가한다. 점검 결과가 할 일을 만들기 때문이다.</p>
 */
@Service
@Transactional(readOnly = true)
public class TaskService {

    private final UserTaskRepository userTaskRepository;
    private final Clock clock;

    public TaskService(UserTaskRepository userTaskRepository, Clock clock) {
        this.userTaskRepository = userTaskRepository;
        this.clock = clock;
    }

    /** 내 할 일 목록(기한 가까운 순, 기한 없는 것은 맨 뒤). */
    public List<UserTaskResponse> getMyTasks(Long userId) {
        LocalDate today = LocalDate.now(clock);
        return userTaskRepository.findByUser_IdOrderByDueOnAscIdAsc(userId).stream()
                .map(task -> UserTaskResponse.from(task, today))
                .toList();
    }

    /**
     * 할 일 상태 변경. 요청한 상태에 맞는 엔티티 메서드를 부른다.
     * switch가 enum의 모든 값을 다루므로, TaskStatus에 값이 추가되면 컴파일 에러로 알려준다.
     */
    @Transactional
    public UserTaskResponse updateStatus(Long userId, Long taskId, UpdateTaskStatusRequest request) {
        UserTask task = userTaskRepository.findByIdAndUser_Id(taskId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));

        switch (request.status()) {
            case DONE -> task.complete();
            case NOT_APPLICABLE -> task.markNotApplicable();
            case TODO -> task.reopen();
        }
        return UserTaskResponse.from(task, LocalDate.now(clock));
    }
}
