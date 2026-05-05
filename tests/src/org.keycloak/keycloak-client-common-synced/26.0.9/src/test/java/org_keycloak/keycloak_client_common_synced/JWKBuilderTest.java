/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import org.junit.jupiter.api.Test;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.KeyType;
import org.keycloak.crypto.KeyUse;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.jose.jwk.JWKBuilder;
import org.keycloak.jose.jwk.OKPPublicJWK;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.assertj.core.api.Assertions.assertThat;

public class JWKBuilderTest {
    @Test
    void buildsOkpPublicJwkForEd25519Key() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(Algorithm.Ed25519);
        KeyPair keyPair = generator.generateKeyPair();

        JWK jwk = JWKBuilder.create()
                .kid("ed25519-key")
                .algorithm(Algorithm.EdDSA)
                .okp(keyPair.getPublic());

        assertThat(jwk).isInstanceOf(OKPPublicJWK.class);
        assertThat(jwk.getKeyId()).isEqualTo("ed25519-key");
        assertThat(jwk.getKeyType()).isEqualTo(KeyType.OKP);
        assertThat(jwk.getAlgorithm()).isEqualTo(Algorithm.EdDSA);
        assertThat(jwk.getPublicKeyUse()).isEqualTo(KeyUse.SIG.getSpecName());

        OKPPublicJWK okpJwk = (OKPPublicJWK) jwk;
        assertThat(okpJwk.getCrv()).isEqualTo(Algorithm.Ed25519);
        assertThat(okpJwk.getX()).isNotBlank();
    }
}
