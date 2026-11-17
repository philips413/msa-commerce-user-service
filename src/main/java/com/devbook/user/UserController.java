package com.devbook.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.List;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
class UserController {

    /** Gateway 가 검증된 JWT 의 sub 를 넣어 주는 헤더. 외부에서 온 값은 엣지가 지운다 (ADR 0005). */
    static final String USER_ID_HEADER = "X-User-Id";

    record SignUpRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 100) String name,
            // bcrypt 는 72바이트까지만 반영하므로 상한을 둔다
            @NotBlank @Size(min = 8, max = 72) String password) {}

    record UserResponse(long id, String email, String name, Instant createdAt) {
        static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getName(), u.getCreatedAt());
        }
    }

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    ResponseEntity<?> signUp(@Valid @RequestBody SignUpRequest request) {
        if (Passwords.exceedsBcryptLimit(request.password())) {
            return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_FAILED",
                    "비밀번호가 너무 깁니다. 영문 기준 72자, 한글 기준 24자 이내로 입력해 주세요."));
        }
        return switch (userService.signUp(request.email(), request.name(), request.password())) {
            case UserService.SignUpResult.Created c ->
                    ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(c.user()));
            case UserService.SignUpResult.DuplicateEmail d ->
                    ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(new ErrorResponse("EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다."));
        };
    }

    @GetMapping("/me")
    ResponseEntity<?> me(HttpServletRequest httpRequest) {
        Long userId = authenticatedUserId(httpRequest);
        if (userId == null) {
            // Gateway 가 보호 경로에서 항상 붙인다. 없으면 Gateway 를 거치지 않은 호출이다
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("UNAUTHENTICATED", "로그인이 필요합니다."));
        }
        return userService.find(userId)
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(UserResponse.from(u)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("NOT_FOUND", "회원을 찾을 수 없습니다.")));
    }

    /**
     * Gateway 가 붙인 회원 id. 값이 정확히 하나일 때만 믿는다 — 누군가 같은 헤더를 미리 넣어 보내
     * 값이 둘 이상이 되면(사칭 시도) 거절한다. 엣지도 외부 값을 지우지만 이중으로 막는다.
     */
    static Long authenticatedUserId(HttpServletRequest request) {
        List<String> values = Collections.list(request.getHeaders(USER_ID_HEADER));
        if (values.size() != 1) {
            return null;
        }
        try {
            return Long.valueOf(values.get(0).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
