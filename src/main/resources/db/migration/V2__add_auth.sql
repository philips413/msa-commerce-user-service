-- 1단계 가입자는 비밀번호가 없다(NULL) → 로그인 불가. 로컬 데이터라 재가입으로 정리한다.
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);

-- 리프레시 토큰은 원문이 아니라 SHA-256 해시만 저장한다.
-- family_id: 한 번의 로그인에서 회전으로 이어지는 토큰 묶음. 재사용이 탐지되면 묶음 전체를 폐기한다.
CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    family_id  UUID        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
