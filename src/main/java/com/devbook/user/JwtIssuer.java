package com.devbook.user;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰(JWT, ES256) 발급과 공개키(JWKS) 제공.
 * 개인키는 이 서비스만 가진다. Gateway 는 JWKS 의 공개키로 서명만 검증한다 (ADR 0005).
 * 스레드 안전: 생성 후 상태가 바뀌지 않는다.
 */
@Component
class JwtIssuer {

    private final AuthProperties props;
    private final Clock clock;
    private final ECKey signingKey;
    private final ECDSASigner signer;

    JwtIssuer(AuthProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            ECPrivateKey privateKey = (ECPrivateKey) keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(readPem(props.privateKeyPath())));
            ECPublicKey publicKey = (ECPublicKey) keyFactory.generatePublic(
                    new X509EncodedKeySpec(readPem(props.publicKeyPath())));
            this.signingKey = new ECKey.Builder(Curve.P_256, publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.ES256)
                    .keyIDFromThumbprint()
                    .build();
            this.signer = new ECDSASigner(signingKey);
        } catch (IOException | GeneralSecurityException | JOSEException e) {
            // 서명키 없이는 로그인이 불가능하다. 기동 단계에서 바로 실패시킨다
            throw new IllegalStateException("JWT signing key could not be loaded from "
                    + props.privateKeyPath() + " / " + props.publicKeyPath(), e);
        }
    }

    String issueAccessToken(long userId) {
        Instant now = Instant.now(clock);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(props.issuer())
                .audience(props.audience())
                .subject(String.valueOf(userId))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(props.accessTokenTtl())))
                .jwtID(UUID.randomUUID().toString())
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(signingKey.getKeyID())
                .type(JOSEObjectType.JWT)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to sign access token", e);
        }
        return jwt.serialize();
    }

    /** 공개키만 담긴 JWKS. */
    Map<String, Object> publicJwks() {
        return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }

    private static byte[] readPem(Path path) throws IOException {
        String body = Files.readString(path)
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
