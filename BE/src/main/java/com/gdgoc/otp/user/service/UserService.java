package com.gdgoc.otp.user.service;

import com.gdgoc.otp.global.auth.LoginUser;
import com.gdgoc.otp.global.exception.BusinessException;
import com.gdgoc.otp.global.exception.ErrorCode;
import com.gdgoc.otp.user.domain.User;
import com.gdgoc.otp.user.dto.AgreeTermsRequest;
import com.gdgoc.otp.user.dto.UpdateProfileRequest;
import com.gdgoc.otp.user.dto.UserResponse;
import com.gdgoc.otp.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 사용자 관련 비즈니스 로직.
 *
 * <p>트랜잭션 규칙(서비스 전체 공통):</p>
 * <ul>
 *   <li>클래스에 {@code @Transactional(readOnly = true)}: 기본은 읽기 전용. 조회 성능이 조금 좋아지고,
 *       실수로 데이터를 바꾸는 것을 막는다.</li>
 *   <li>데이터를 바꾸는 메서드에만 {@code @Transactional}을 다시 붙여 쓰기를 허용한다.</li>
 *   <li>메서드 안에서 예외(RuntimeException)가 나면 그 메서드의 DB 변경은 모두 롤백된다.</li>
 * </ul>
 *
 * <p>변경 감지(dirty checking): 트랜잭션 안에서 조회한 엔티티의 값을 바꾸면,
 * save()를 부르지 않아도 트랜잭션이 끝날 때 JPA가 바뀐 부분을 UPDATE로 보낸다.
 * 그래서 updateProfile에는 save 호출이 없다.</p>
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * 생성자 주입: 스프링이 필요한 객체(빈)를 생성자 인자로 넣어준다.
     * 필드에 @Autowired를 붙이는 것보다 테스트에서 가짜 객체를 넣기 쉽고, final로 둘 수 있다.
     */
    public UserService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * 식별자로 사용자를 찾고, 없으면 새로 만든다(첫 로그인 = 가입).
     * CurrentUserArgumentResolver가 매 요청마다 호출한다.
     *
     * <p>알려진 한계: 같은 신규 사용자의 첫 요청 두 개가 동시에 들어오면 둘 다 "없음"으로 보고 INSERT를 시도해
     * 하나는 unique 제약에 걸려 409가 난다. 데모에서는 드문 상황이라 두었고,
     * 실제 인증을 붙일 때 로그인 API 한 곳에서만 가입하도록 바꾸면 사라진다.</p>
     */
    @Transactional
    public LoginUser findOrCreate(String authSubject) {
        User user = userRepository.findByAuthSubject(authSubject)
                .orElseGet(() -> userRepository.save(User.create(authSubject)));
        return new LoginUser(user.getId());
    }

    /** 내 정보 조회. */
    public UserResponse getMe(Long userId) {
        return UserResponse.from(getUser(userId));
    }

    /** 점검 입력값(출생연도, 입사일, 중소기업 여부) 수정. */
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getUser(userId);
        user.updateProfile(request.birthYear(), request.joinedOn(), request.isSmeEmployee());
        return UserResponse.from(user);
    }

    /** 약관 동의 기록. 이미 동의했어도 새 버전으로 덮어쓴다(재동의). */
    @Transactional
    public UserResponse agreeToTerms(Long userId, AgreeTermsRequest request) {
        User user = getUser(userId);
        user.agreeToTerms(request.termsVersion(), Instant.now(clock));
        return UserResponse.from(user);
    }

    /**
     * 다른 서비스(명세서, 할 일 등)가 User 엔티티가 필요할 때 쓰는 공통 조회.
     * 없으면 404 예외를 던지므로 호출하는 쪽에서 null 검사를 할 필요가 없다.
     */
    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
