/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_auth0.java_jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.Test;

public class JsonNodeClaimTest {
    @Test
    public void decodesArrayClaimAsJavaArray() {
        String token = JWT.create()
                .withArrayClaim("roles", new String[] {"admin", "writer"})
                .sign(Algorithm.none());

        DecodedJWT decodedJWT = JWT.decode(token);
        String[] roles = decodedJWT.getClaim("roles").asArray(String.class);

        assertThat(roles).containsExactly("admin", "writer");
    }
}
