/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import org.junit.jupiter.api.Test;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.KeyType;
import org.keycloak.crypto.KeyUse;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.jose.jwk.JWKBuilder;
import org.keycloak.jose.jwk.RSAPublicJWK;

import static org.assertj.core.api.Assertions.assertThat;

public class JWKBuilderTest {
    private static final String KEY_ID = "test-rsa-key";

    @Test
    void buildsRsaPublicJwkWithConfiguredMetadata() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KeyType.RSA);
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        JWK jwk = JWKBuilder.create()
                .kid(KEY_ID)
                .algorithm(Algorithm.RS256)
                .rsa(keyPair.getPublic(), KeyUse.ENC);

        assertThat(jwk).isInstanceOf(RSAPublicJWK.class);
        RSAPublicJWK rsaJwk = (RSAPublicJWK) jwk;
        assertThat(rsaJwk.getKeyId()).isEqualTo(KEY_ID);
        assertThat(rsaJwk.getKeyType()).isEqualTo(KeyType.RSA);
        assertThat(rsaJwk.getAlgorithm()).isEqualTo(Algorithm.RS256);
        assertThat(rsaJwk.getPublicKeyUse()).isEqualTo("enc");
        assertThat(rsaJwk.getModulus()).isNotBlank();
        assertThat(rsaJwk.getPublicExponent()).isNotBlank();
    }
}
