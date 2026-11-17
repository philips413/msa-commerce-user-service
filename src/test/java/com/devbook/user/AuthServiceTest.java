package com.devbook.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devbook.user.AuthService.AuthResult;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T00:00:00Z");
    private static final String RAW = "raw-refresh-token";

    private UserRepository users;
    private RefreshTokenRepository refreshTokens;
    private PasswordEncoder passwordEncoder;
    private JwtIssuer jwtIssuer;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        refreshTokens = mock(RefreshTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtIssuer = mock(JwtIssuer.class);
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}dummy");
        when(jwtIssuer.issueAccessToken(anyLong())).thenReturn("access-token");
        AuthProperties props = new AuthProperties("devbook", "devbook-api", Duration.ofMinutes(15),
                Duration.ofDays(14), Path.of("unused"), Path.of("unused"), true);
        service = new AuthService(users, refreshTokens, passwordEncoder, jwtIssuer, props,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void 없는_이메일이어도_비밀번호_비교를_한_번_수행하고_거절한다() {
        when(users.findByEmail("none@example.com")).thenReturn(Optional.empty());

        AuthResult result = service.login("none@example.com", "password1");

        assertThat(result).isInstanceOf(AuthResult.Rejected.class);
        verify(passwordEncoder).matches("password1", "{bcrypt}dummy");
    }

    @Test
    void 리프레시하면_쓴_토큰은_폐기되고_같은_세션으로_새_토큰이_발급된다() {
        UUID family = UUID.randomUUID();
        RefreshToken current = new RefreshToken(7L, AuthService.sha256(RAW), family, NOW.plusSeconds(60), NOW);
        when(refreshTokens.findByTokenHash(AuthService.sha256(RAW))).thenReturn(Optional.of(current));
        when(refreshTokens.revokeIfActive(any(), eq(NOW))).thenReturn(1);

        AuthResult result = service.refresh(RAW);

        assertThat(result).isInstanceOf(AuthResult.Issued.class);
        AuthResult.Issued issued = (AuthResult.Issued) result;
        assertThat(issued.userId()).isEqualTo(7L);
        assertThat(issued.refreshToken()).isNotEqualTo(RAW);
        verify(refreshTokens).revokeIfActive(any(), eq(NOW));
        verify(refreshTokens).save(any(RefreshToken.class));
    }

    @Test
    void 폐기된_지_오래된_리프레시_토큰이_다시_오면_세션_전체를_폐기하고_거절한다() {
        UUID family = UUID.randomUUID();
        RefreshToken reused = new RefreshToken(7L, AuthService.sha256(RAW), family, NOW.plusSeconds(600), NOW);
        reused.revoke(NOW.minusSeconds(60));
        when(refreshTokens.findByTokenHash(AuthService.sha256(RAW))).thenReturn(Optional.of(reused));

        AuthResult result = service.refresh(RAW);

        assertThat(result).isInstanceOf(AuthResult.Rejected.class);
        verify(refreshTokens).revokeFamily(eq(family), eq(NOW));
        verify(refreshTokens, never()).save(any(RefreshToken.class));
    }

    @Test
    void 방금_폐기된_토큰이_다시_오면_동시_요청으로_보고_세션은_살려_둔다() {
        RefreshToken justRotated = new RefreshToken(7L, AuthService.sha256(RAW), UUID.randomUUID(), NOW.plusSeconds(600), NOW);
        justRotated.revoke(NOW.minusSeconds(3));
        when(refreshTokens.findByTokenHash(AuthService.sha256(RAW))).thenReturn(Optional.of(justRotated));

        assertThat(service.refresh(RAW)).isInstanceOf(AuthResult.Rejected.class);
        verify(refreshTokens, never()).revokeFamily(any(), any());
    }

    @Test
    void 동시_요청에_밀려_폐기에_실패하면_발급하지_않고_세션도_살려_둔다() {
        RefreshToken current = new RefreshToken(7L, AuthService.sha256(RAW), UUID.randomUUID(), NOW.plusSeconds(600), NOW);
        when(refreshTokens.findByTokenHash(AuthService.sha256(RAW))).thenReturn(Optional.of(current));
        when(refreshTokens.revokeIfActive(any(), eq(NOW))).thenReturn(0);

        assertThat(service.refresh(RAW)).isInstanceOf(AuthResult.Rejected.class);
        verify(refreshTokens, never()).save(any(RefreshToken.class));
        verify(refreshTokens, never()).revokeFamily(any(), any());
    }

    @Test
    void 만료된_리프레시_토큰은_거절한다() {
        RefreshToken expired = new RefreshToken(7L, AuthService.sha256(RAW), UUID.randomUUID(), NOW, NOW.minusSeconds(60));
        when(refreshTokens.findByTokenHash(AuthService.sha256(RAW))).thenReturn(Optional.of(expired));

        assertThat(service.refresh(RAW)).isInstanceOf(AuthResult.Rejected.class);
        verify(refreshTokens, never()).save(any(RefreshToken.class));
    }
}
