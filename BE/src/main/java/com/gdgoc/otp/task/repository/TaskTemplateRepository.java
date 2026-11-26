package com.gdgoc.otp.task.repository;

import com.gdgoc.otp.task.domain.TaskTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * TaskTemplate 테이블 접근.
 * 템플릿은 id가 아니라 key("sme-reduction-application")로 찾는다. id는 DB마다 다를 수 있다.
 */
public interface TaskTemplateRepository extends JpaRepository<TaskTemplate, Long> {

    Optional<TaskTemplate> findByKey(String key);
}
