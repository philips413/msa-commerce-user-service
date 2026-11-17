package com.devbook.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 리프레시 토큰 기록. 원문은 저장하지 않고 SHA-256 해시만 둔다. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefreshToken() {
        // JPA
    }

    RefreshToken(long userId, String tokenHash, UUID familyId, Instant expiresAt, Instant createdAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    Long id() { return id; }
    long userId() { return userId; }
    UUID familyId() { return familyId; }

    boolean isRevoked() {
        return revokedAt != null;
    }

    /** 폐기된 지 window 이내인가. 같은 브라우저가 거의 동시에 보낸 갱신 요청을 탈취와 구분하는 데 쓴다. */
    boolean wasRevokedWithin(Instant now, java.time.Duration window) {
        return revokedAt != null && revokedAt.plus(window).isAfter(now);
    }

    boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
