package com.gdgoc.otp.global.auth;

import com.gdgoc.otp.global.exception.BusinessException;
import com.gdgoc.otp.global.exception.ErrorCode;
import com.gdgoc.otp.user.service.UserService;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * ⚠️⚠️ 개발·데모 전용 임시 인증. 실제 서비스로 배포하기 전에 반드시 교체해야 한다. ⚠️⚠️
 *
 * <p>동작: 요청 헤더 {@value #HEADER_NAME}의 값을 "사용자 식별자"로 그대로 믿는다.
 * 그 값으로 users.auth_subject를 찾고, 없으면 새로 가입시킨 뒤 {@link LoginUser}를 만든다.</p>
 *
 * <pre>
 * GET /api/payslips
 * X-User-Subject: chulsoo     → chulsoo 사용자로 처리
 * </pre>
 *
 * <p>보안 위험: 누구나 헤더에 남의 식별자를 넣으면 그 사람 행세를 할 수 있다(비밀번호·토큰 검증이 없음).</p>
 *
 * <p>나중에 실제 인증(Google/카카오 로그인, JWT 등)으로 바꾸는 방법:
 * 이 클래스의 {@link #resolveArgument}에서 "헤더 읽기"를 "토큰 검증 후 sub 꺼내기"로 바꾸면 된다.
 * 결과가 똑같이 LoginUser이므로 컨트롤러·서비스는 고칠 필요가 없다.</p>
 *
 * <p>HandlerMethodArgumentResolver: 스프링 MVC가 컨트롤러 메서드를 호출하기 직전에
 * 파라미터 값을 채우는 확장 지점이다. {@link #supportsParameter}가 true인 파라미터에 대해
 * {@link #resolveArgument}의 반환값을 넣어준다. 등록은 WebConfig에서 한다.</p>
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER_NAME = "X-User-Subject";

    /** users.auth_subject 컬럼 길이(VARCHAR 255)를 넘으면 DB 오류가 나므로 미리 막는다. */
    private static final int MAX_SUBJECT_LENGTH = 255;

    private final UserService userService;

    public CurrentUserArgumentResolver(UserService userService) {
        this.userService = userService;
    }

    /** @CurrentUser가 붙어 있고 타입이 LoginUser인 파라미터만 처리한다. */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && LoginUser.class.equals(parameter.getParameterType());
    }

    @Override
    public LoginUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                     NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String subject = webRequest.getHeader(HEADER_NAME);

        // 헤더가 없거나 비어 있으면 "로그인 안 함"으로 본다 → 401
        if (subject == null || subject.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED,
                    "로그인이 필요합니다. (개발용: " + HEADER_NAME + " 헤더를 넣어 주세요)");
        }
        subject = subject.trim();
        if (subject.length() > MAX_SUBJECT_LENGTH) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "사용자 식별자가 너무 깁니다.");
        }

        // 처음 보는 식별자면 자동 가입. 실제 인증에서도 "첫 로그인 = 가입" 흐름은 같다.
        return userService.findOrCreate(subject);
    }
}
