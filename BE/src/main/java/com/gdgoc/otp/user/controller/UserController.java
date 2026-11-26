package com.gdgoc.otp.user.controller;

import com.gdgoc.otp.global.auth.CurrentUser;
import com.gdgoc.otp.global.auth.LoginUser;
import com.gdgoc.otp.user.dto.AgreeTermsRequest;
import com.gdgoc.otp.user.dto.UpdateProfileRequest;
import com.gdgoc.otp.user.dto.UserResponse;
import com.gdgoc.otp.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 정보 API.
 *
 * <p>컨트롤러의 역할은 "HTTP ↔ 서비스 연결"뿐이다: URL·메서드 매핑, 요청 검증(@Valid),
 * 현재 사용자 꺼내기(@CurrentUser), 서비스 호출, 결과 반환.
 * 비즈니스 판단(권한, 상태 변경 규칙)은 서비스와 엔티티에 둔다.</p>
 *
 * <p>{@code @RestController}: 반환값을 JSON으로 바꿔 응답 본문에 쓴다.
 * 에러는 서비스가 던진 예외를 GlobalExceptionHandler가 처리하므로 여기서 try-catch하지 않는다.</p>
 *
 * <p>모든 요청에 {@code X-User-Subject} 헤더가 필요하다(임시 인증).</p>
 */
@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 내 정보 조회. 처음 요청이면 자동 가입된 빈 정보가 나온다. */
    @GetMapping
    public UserResponse getMe(@CurrentUser LoginUser loginUser) {
        return userService.getMe(loginUser.id());
    }

    /** 점검 입력값 수정(전체 교체). */
    @PutMapping("/profile")
    public UserResponse updateProfile(@CurrentUser LoginUser loginUser,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(loginUser.id(), request);
    }

    /** 약관 동의. */
    @PostMapping("/terms-agreement")
    public UserResponse agreeToTerms(@CurrentUser LoginUser loginUser,
                                     @Valid @RequestBody AgreeTermsRequest request) {
        return userService.agreeToTerms(loginUser.id(), request);
    }
}
