package com.devbook.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    /** 계약 값 (docs/api-contract.md). 이름·Path 를 바꾸면 기존 로그인 세션이 끊긴다. */
    static final String REFRESH_COOKIE = "devbook_rt";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 72) String password) {}

    record TokenResponse(String accessToken, String tokenType, long expiresIn) {}

    private final AuthService authService;
    private final AuthProperties props;

    AuthController(AuthService authService, AuthProperties props) {
        this.authService = authService;
        this.props = props;
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        return switch (authService.login(request.email(), request.password())) {
            case AuthService.AuthResult.Issued issued -> tokenResponse(issued);
            case AuthService.AuthResult.Rejected r -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("INVALID_CREDENTIALS", "이메일 또는 비밀번호를 확인해 주세요."));
        };
    }

    @PostMapping("/refresh")
    ResponseEntity<?> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        return switch (authService.refresh(refreshToken)) {
            case AuthService.AuthResult.Issued issued -> tokenResponse(issued);
            case AuthService.AuthResult.Rejected r -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                    .body(new ErrorResponse("INVALID_REFRESH_TOKEN", "다시 로그인해 주세요."));
        };
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<TokenResponse> tokenResponse(AuthService.AuthResult.Issued issued) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(issued.refreshToken(), props.refreshTokenTtl()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new TokenResponse(issued.accessToken(), "Bearer", props.accessTokenTtl().toSeconds()));
    }

    /** HttpOnly: 스크립트가 못 읽음. SameSite=Strict: 다른 사이트에서 못 보냄. Path: 인증 API 에만 실려 감. */
    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
