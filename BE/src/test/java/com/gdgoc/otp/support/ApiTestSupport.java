package com.gdgoc.otp.support;

import com.gdgoc.otp.global.auth.CurrentUserArgumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * API(컨트롤러) 테스트의 부모 클래스.
 *
 * <p>MockMvc: 실제 서버(포트)를 띄우지 않고 스프링 MVC에 가짜 HTTP 요청을 넣어 응답을 검사하는 도구.
 * 컨트롤러 → 서비스 → Repository → 임시 PostgreSQL까지 전부 실제 코드로 실행된다.</p>
 *
 * <p>{@code @Transactional}: 테스트가 끝나면 롤백되어 테스트끼리 데이터가 섞이지 않는다.
 * MockMvc는 테스트와 같은 스레드에서 돌기 때문에 이 롤백이 API 호출에도 적용된다.</p>
 *
 * <p>요청 헬퍼(getAs 등)는 임시 인증 헤더 {@code X-User-Subject}를 자동으로 붙인다.</p>
 */
@Transactional
public abstract class ApiTestSupport extends IntegrationTestSupport {

    @Autowired
    WebApplicationContext context;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    protected MockHttpServletRequestBuilder getAs(String subject, String url, Object... vars) {
        return get(url, vars).header(CurrentUserArgumentResolver.HEADER_NAME, subject);
    }

    protected MockHttpServletRequestBuilder putJsonAs(String subject, String json, String url, Object... vars) {
        return put(url, vars).header(CurrentUserArgumentResolver.HEADER_NAME, subject)
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    protected MockHttpServletRequestBuilder postJsonAs(String subject, String json, String url, Object... vars) {
        return post(url, vars).header(CurrentUserArgumentResolver.HEADER_NAME, subject)
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    protected MockHttpServletRequestBuilder patchJsonAs(String subject, String json, String url, Object... vars) {
        return patch(url, vars).header(CurrentUserArgumentResolver.HEADER_NAME, subject)
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }
}
