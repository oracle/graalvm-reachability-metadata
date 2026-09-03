/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;

import org.junit.jupiter.api.Test;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.KeyType;
import org.keycloak.crypto.KeyUse;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.jose.jwk.JWKBuilder;
import org.keycloak.jose.jwk.OKPPublicJWK;

public class JWKBuilderTest {
    @Test
    void createsOkpJwkForEd25519PublicKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(Algorithm.Ed25519);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();

        JWK jwk = JWKBuilder.create()
                .kid("test-key")
                .algorithm(Algorithm.EdDSA)
                .okp(publicKey);

        assertThat(jwk).isInstanceOf(OKPPublicJWK.class);
        OKPPublicJWK okpJwk = (OKPPublicJWK) jwk;
        assertThat(okpJwk.getKeyId()).isEqualTo("test-key");
        assertThat(okpJwk.getKeyType()).isEqualTo(KeyType.OKP);
        assertThat(okpJwk.getAlgorithm()).isEqualTo(Algorithm.EdDSA);
        assertThat(okpJwk.getPublicKeyUse()).isEqualTo(KeyUse.SIG.getSpecName());
        assertThat(okpJwk.getCrv()).isEqualTo(Algorithm.Ed25519);
        assertThat(okpJwk.getX()).isNotBlank();
    }
}
