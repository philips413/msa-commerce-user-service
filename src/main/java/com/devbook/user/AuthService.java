package com.devbook.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인·리프레시 회전·로그아웃 (ADR 0005). */
@Service
class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int REFRESH_TOKEN_BYTES = 32;
    /** 폐기 직후 이 시간 안에 같은 토큰이 다시 오면 탈취가 아니라 동시 요청(새로고침 경합 등)으로 보고 세션은 살려 둔다. */
    private static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    sealed interface AuthResult {
        /** refreshToken 은 원문. 쿠키로만 내보내고 로그에 남기지 않는다. */
        record Issued(long userId, String accessToken, String refreshToken) implements AuthResult {}
        record Rejected() implements AuthResult {}
    }

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final AuthProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    /** 없는 이메일에도 같은 비용의 비교를 해서, 응답 시간으로 가입 여부를 알 수 없게 한다. */
    private final String dummyHash;

    AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                JwtIssuer jwtIssuer, AuthProperties props, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.props = props;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    AuthResult login(String email, String rawPassword) {
        if (Passwords.exceedsBcryptLimit(rawPassword)) {
            // 가입 단계에서 막히므로 이런 비밀번호를 가진 회원은 없다. bcrypt 가 예외를 던지기 전에 거절한다
            return new AuthResult.Rejected();
        }
        Optional<User> user = users.findByEmail(email);
        Optional<String> hash = user.flatMap(User::passwordHash);
        if (hash.isEmpty()) {
            passwordEncoder.matches(rawPassword, dummyHash);
            return new AuthResult.Rejected();
        }
        if (!passwordEncoder.matches(rawPassword, hash.get())) {
            return new AuthResult.Rejected();
        }
        return issue(user.get().getId(), UUID.randomUUID());
    }

    @Transactional
    AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return new AuthResult.Rejected();
        }
        Optional<RefreshToken> found = refreshTokens.findByTokenHash(sha256(rawRefreshToken));
        if (found.isEmpty()) {
            return new AuthResult.Rejected();
        }
        RefreshToken token = found.get();
        Instant now = Instant.now(clock);
        if (token.isExpired(now)) {
            return new AuthResult.Rejected();
        }
        if (token.isRevoked()) {
            return rejectReuse(token, now);
        }
        // 조건부 UPDATE 로 "살아 있으면 폐기"를 한 번에 한다. 동시에 온 두 요청 중 하나만 1 을 받는다
        if (refreshTokens.revokeIfActive(token.id(), now) == 1) {
            return issue(token.userId(), token.familyId());
        }
        // 바로 이 순간 다른 요청이 먼저 회전시켰다 = 같은 브라우저의 동시 요청. 거절만 하고 세션은 살린다
        log.info("refresh token raced with a concurrent refresh userId={} family={}", token.userId(), token.familyId());
        return new AuthResult.Rejected();
    }

    private AuthResult rejectReuse(RefreshToken token, Instant now) {
        if (token.isRevoked() && token.wasRevokedWithin(now, REUSE_GRACE)) {
            // 방금 회전된 토큰 = 같은 브라우저의 거의 동시 요청일 가능성이 높다. 거절만 하고 세션은 살린다
            log.info("refresh token reused within grace window userId={} family={}", token.userId(), token.familyId());
            return new AuthResult.Rejected();
        }
        // 이미 회전된 토큰이 다시 왔다 = 원문이 다른 곳에 있다. 이 로그인 세션 전체를 끊는다
        int revoked = refreshTokens.revokeFamily(token.familyId(), now);
        log.warn("refresh token reuse detected userId={} family={} revoked={}",
                token.userId(), token.familyId(), revoked);
        return new AuthResult.Rejected();
    }

    @Transactional
    void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(sha256(rawRefreshToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.familyId(), Instant.now(clock)));
    }

    private AuthResult.Issued issue(long userId, UUID familyId) {
        Instant now = Instant.now(clock);
        String rawRefreshToken = newRandomToken();
        refreshTokens.save(new RefreshToken(userId, sha256(rawRefreshToken), familyId,
                now.plus(props.refreshTokenTtl()), now));
        return new AuthResult.Issued(userId, jwtIssuer.issueAccessToken(userId), rawRefreshToken);
    }

    private String newRandomToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // 모든 JVM 이 SHA-256 을 제공해야 한다 (Java SE 명세)
            throw new IllegalStateException(e);
        }
    }
}
