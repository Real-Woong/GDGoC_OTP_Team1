package com.gdgoc.otp.global.config;

import com.gdgoc.otp.global.auth.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
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

    public WebConfig(CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    /** 컨트롤러 파라미터의 @CurrentUser LoginUser를 채우는 리졸버를 등록한다. */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    // TODO: 프론트 개발 서버(예: http://localhost:3000)에서 직접 호출하려면 CORS 설정이 필요하다.
    //       프론트 주소가 정해지면 addCorsMappings를 추가한다.
}
