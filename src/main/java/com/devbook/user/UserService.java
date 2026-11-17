package com.devbook.user;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
class UserService {

    sealed interface SignUpResult {
        record Created(User user) implements SignUpResult {}
        record DuplicateEmail() implements SignUpResult {}
    }

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    UserService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    SignUpResult signUp(String email, String name, String rawPassword) {
        if (users.existsByEmail(email)) {
            return new SignUpResult.DuplicateEmail();
        }
        String hash = passwordEncoder.encode(rawPassword);
        try {
            return new SignUpResult.Created(users.saveAndFlush(
                    new User(email, name, hash, Instant.now(clock).truncatedTo(ChronoUnit.MICROS))));
        } catch (DataIntegrityViolationException e) {
            // 동시 가입 경합: existsByEmail 통과 후 다른 요청이 먼저 저장한 경우. UNIQUE 제약이 최종 방어선이다.
            return new SignUpResult.DuplicateEmail();
        }
    }

    Optional<User> find(long id) {
        return users.findById(id);
    }
}
