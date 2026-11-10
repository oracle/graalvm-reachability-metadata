/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_c4_soft_springaddons.spring_addons_starter_oidc;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.c4_soft.springaddons.security.oidc.starter.SpringAddonsJwtValidators;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringAddonsJwtValidatorsTest {
    @Test
    void validatesIssuerAudienceTimestampsAndAdditionalClaims() {
        OAuth2TokenValidator<Jwt> validator = SpringAddonsJwtValidators.createDefault(
                Optional.of(URI.create("https://issuer.example")), Optional.of("catalog"), List.of("at+jwt"),
                new JwtClaimValidator<String>("client_id", "frontend"::equals));
        Jwt valid = token("application/AT+JWT").build();
        assertThat(validator.validate(valid).hasErrors()).isFalse();
        assertThat(validator.validate(token("at+jwt").issuer("https://other.example").build()).hasErrors()).isTrue();
        assertThat(validator.validate(token("at+jwt").audience(List.of("other-api")).build()).hasErrors()).isTrue();
        assertThat(validator.validate(token("at+jwt").claim("client_id", "unknown").build()).hasErrors()).isTrue();
        assertThat(validator.validate(token("at+jwt")
                .issuedAt(Instant.now().minusSeconds(900))
                .expiresAt(Instant.now().minusSeconds(300)).build()).hasErrors()).isTrue();
    }

    @Test
    void enforcesConfiguredTokenTypesAndDefaultMissingTypePolicy() {
        OAuth2TokenValidator<Jwt> strict = SpringAddonsJwtValidators.typValidator(List.of("at+jwt"));
        assertThat(strict.validate(token("AT+JWT").build()).hasErrors()).isFalse();
        assertThat(strict.validate(token("application/at+jwt").build()).hasErrors()).isFalse();
        assertThat(strict.validate(token("JWT").build()).hasErrors()).isTrue();
        assertThat(strict.validate(token(null).build()).hasErrors()).isTrue();

        OAuth2TokenValidator<Jwt> defaults = SpringAddonsJwtValidators.typValidator(List.of());
        assertThat(defaults.validate(token(null).build()).hasErrors()).isFalse();
        assertThat(defaults.validate(token("JWT").build()).hasErrors()).isFalse();
        assertThat(defaults.validate(token("at+jwt").build()).hasErrors()).isFalse();
        assertThat(defaults.validate(token("unrecognized").build()).hasErrors()).isTrue();
    }

    private static Jwt.Builder token(String type) {
        Jwt.Builder builder = Jwt.withTokenValue("encoded-token")
                .header("alg", "RS256")
                .issuer("https://issuer.example")
                .subject("subject-id")
                .audience(List.of("catalog"))
                .claim("client_id", "frontend")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600));
        if (type != null) {
            builder.header("typ", type);
        }
        return builder;
    }
}
