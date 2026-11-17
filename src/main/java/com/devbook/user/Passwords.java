package com.devbook.user;

import java.nio.charset.StandardCharsets;

/** bcrypt 는 72 '바이트'까지만 받는다. 한글은 글자당 3바이트라 글자 수 검증(@Size)만으로는 부족하다. */
final class Passwords {

    static final int BCRYPT_MAX_BYTES = 72;

    private Passwords() {
    }

    static boolean exceedsBcryptLimit(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }
}
