package com.gdgoc.otp.task;

import com.gdgoc.otp.support.ApiTestSupport;
import com.gdgoc.otp.task.domain.TaskTemplate;
import com.gdgoc.otp.task.domain.UserTask;
import com.gdgoc.otp.task.repository.TaskTemplateRepository;
import com.gdgoc.otp.task.repository.UserTaskRepository;
import com.gdgoc.otp.user.domain.User;
import com.gdgoc.otp.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 할 일 API 테스트.
 * 할 일 생성은 5단계(점검)에서 추가되므로 테스트 데이터는 Repository로 직접 넣는다.
 */
class TaskApiTest extends ApiTestSupport {

    @Autowired UserRepository userRepository;
    @Autowired TaskTemplateRepository taskTemplateRepository;
    @Autowired UserTaskRepository userTaskRepository;

    Long overdueTaskId;

    @BeforeEach
    void setUpTasks() {
        User chulsoo = userRepository.save(User.create("chulsoo"));
        userRepository.save(User.create("younghee"));
        TaskTemplate sme = taskTemplateRepository.findByKey("sme-reduction-application").orElseThrow();
        TaskTemplate dependent = taskTemplateRepository.findByKey("dependent-registration").orElseThrow();

        userTaskRepository.save(UserTask.assign(chulsoo, dependent, null, null));
        // 이미 지난 날짜 → overdue
        overdueTaskId = userTaskRepository.save(UserTask.assign(chulsoo, sme, null, LocalDate.of(2020, 1, 31))).getId();
    }

    @Test
    void 내_할일_목록은_기한순이고_기한초과를_표시한다() throws Exception {
        mockMvc.perform(getAs("chulsoo", "/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].templateKey").value("sme-reduction-application"))
                .andExpect(jsonPath("$[0].title").value("중소기업 취업자 소득세 감면 신청서 제출"))
                .andExpect(jsonPath("$[0].status").value("TODO"))
                .andExpect(jsonPath("$[0].overdue").value(true))
                .andExpect(jsonPath("$[1].dueOn").isEmpty());
    }

    @Test
    void 완료하면_기한초과가_풀리고_되돌릴_수_있다() throws Exception {
        mockMvc.perform(patchJsonAs("chulsoo", "{\"status\":\"DONE\"}", "/api/tasks/{id}/status", overdueTaskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.overdue").value(false));

        mockMvc.perform(patchJsonAs("chulsoo", "{\"status\":\"TODO\"}", "/api/tasks/{id}/status", overdueTaskId))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void 남의_할일은_404() throws Exception {
        mockMvc.perform(patchJsonAs("younghee", "{\"status\":\"DONE\"}", "/api/tasks/{id}/status", overdueTaskId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    @Test
    void 없는_상태값은_400() throws Exception {
        mockMvc.perform(patchJsonAs("chulsoo", "{\"status\":\"FINISHED\"}", "/api/tasks/{id}/status", overdueTaskId))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patchJsonAs("chulsoo", "{}", "/api/tasks/{id}/status", overdueTaskId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"));
    }
}
