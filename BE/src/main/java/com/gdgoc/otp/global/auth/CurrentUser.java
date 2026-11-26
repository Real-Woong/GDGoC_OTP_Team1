package com.gdgoc.otp.global.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드 파라미터에 붙이면 "지금 요청한 사용자"가 자동으로 들어온다.
 *
 * <pre>
 * {@code @GetMapping("/api/users/me")}
 * public UserResponse me({@code @CurrentUser} LoginUser loginUser) { ... }
 * </pre>
 *
 * <p>실제로 값을 채우는 곳은 {@link CurrentUserArgumentResolver}다.
 * 어노테이션 자체는 "여기에 현재 사용자를 넣어 달라"는 표시일 뿐이다.</p>
 */
@Target(ElementType.PARAMETER)          // 메서드 파라미터에만 붙일 수 있다
@Retention(RetentionPolicy.RUNTIME)     // 실행 중에 스프링이 읽을 수 있어야 한다
public @interface CurrentUser {
}
