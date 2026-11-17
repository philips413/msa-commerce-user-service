package com.devbook.user;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gateway 가 토큰 검증용 공개키를 가져가는 곳. HTTPRoute 에 없으므로 클러스터 안에서만 닿는다. */
@RestController
class JwksController {

    private final JwtIssuer jwtIssuer;

    JwksController(JwtIssuer jwtIssuer) {
        this.jwtIssuer = jwtIssuer;
    }

    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> jwks() {
        return jwtIssuer.publicJwks();
    }
}
