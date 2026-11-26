package com.gdgoc.otp.global.config;

import com.gdgoc.otp.global.auth.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 스프링 MVC 설정.
 *
 * <p>WebMvcConfigurer: 스프링 MVC 기본 설정은 그대로 두고 필요한 부분만 덧붙이는 인터페이스.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final CorsProperties corsProperties;

    public WebConfig(CurrentUserArgumentResolver currentUserArgumentResolver, CorsProperties corsProperties) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
        this.corsProperties = corsProperties;
    }

    /** 컨트롤러 파라미터의 @CurrentUser LoginUser를 채우는 리졸버를 등록한다. */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    /**
     * CORS(Cross-Origin Resource Sharing) 설정.
     *
     * <p>왜 필요한가: 브라우저는 보안상 "다른 출처"(주소·포트가 다른 곳)로의 요청을 기본으로 막는다.
     * 프론트가 http://localhost:3000, 이 API가 http://localhost:8080이면 포트가 달라 다른 출처다.
     * 서버가 "이 출처는 허용한다"는 응답 헤더(Access-Control-Allow-Origin)를 줘야 브라우저가 응답을 프론트에 넘겨준다.</p>
     *
     * <p>CORS는 브라우저만 지키는 규칙이다. curl, Postman, 서버 간 호출은 CORS와 무관하게 동작한다.
     * 즉 CORS는 인증을 대신하지 않는다.</p>
     *
     * <p>Preflight: PATCH·PUT 요청이나 커스텀 헤더(X-User-Subject)가 있는 요청은, 브라우저가 본 요청 전에
     * OPTIONS 요청으로 "보내도 되는지" 먼저 묻는다. 아래 설정이 그 질문에 답한다.</p>
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                // 임시 인증 헤더도 허용해야 브라우저가 보낼 수 있다
                .allowedHeaders("Content-Type", CurrentUserArgumentResolver.HEADER_NAME, "Authorization")
                // 쿠키 인증을 쓰지 않으므로 false. 쿠키 로그인을 도입하면 true로 바꾸고 출처를 정확히 지정해야 한다.
                .allowCredentials(false)
                // preflight 결과를 브라우저가 1시간 기억해 OPTIONS 요청 횟수를 줄인다
                .maxAge(3600);
    }
}
