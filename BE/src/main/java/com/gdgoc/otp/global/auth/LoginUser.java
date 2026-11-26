package com.gdgoc.otp.global.auth;

/**
 * "지금 요청한 사람"을 나타내는 값. 컨트롤러가 {@code @CurrentUser LoginUser loginUser}로 받는다.
 *
 * <p>User 엔티티 대신 id만 담은 작은 record를 쓰는 이유:
 * 엔티티는 DB 연결(트랜잭션) 밖으로 나가면 LAZY 필드 접근 시 에러가 날 수 있다.
 * 컨트롤러는 "누구인지"만 알면 되고, 실제 데이터는 서비스가 id로 다시 조회한다.</p>
 *
 * <p>인증 방식이 바뀌어도 이 record의 모양은 그대로 둔다. 그래야 컨트롤러·서비스 코드를 고칠 필요가 없다.</p>
 *
 * @param id users 테이블의 id
 */
public record LoginUser(Long id) {
}
