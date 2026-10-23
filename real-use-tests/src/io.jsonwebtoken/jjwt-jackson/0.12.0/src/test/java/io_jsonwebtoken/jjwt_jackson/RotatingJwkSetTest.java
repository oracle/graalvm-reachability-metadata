/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_jackson;

import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.RsaPublicJwk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises Jackson's JSON Web Key Set codec for publishing overlapping signing keys during rotation.
public class RotatingJwkSetTest {
    @Test
    @Timeout(60)
    public void publishesPublicKeysAndReloadsThemByKeyId() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair retiringKey = generator.generateKeyPair();
        KeyPair activeKey = generator.generateKeyPair();
        RsaPublicJwk retiringJwk = Jwks.builder()
                .key((RSAPublicKey) retiringKey.getPublic())
                .id("warehouse-retiring")
                .algorithm("RS256")
                .operations().add(Jwks.OP.VERIFY).and()
                .build();
        RsaPublicJwk activeJwk = Jwks.builder()
                .key((RSAPublicKey) activeKey.getPublic())
                .id("warehouse-active")
                .algorithm("RS256")
                .operations().add(Jwks.OP.VERIFY).and()
                .build();
        JwkSet publishedKeys = Jwks.set().add(retiringJwk).add(activeJwk).build();
        JacksonSerializer<Map<String, ?>> serializer = new JacksonSerializer<>();
        byte[] discoveryResponse = serializer.serialize(publishedKeys);

        JacksonDeserializer<Map<String, ?>> deserializer = new JacksonDeserializer<>();
        Map<String, ?> document = deserializer.deserialize(discoveryResponse);
        List<?> entries = (List<?>) document.get("keys");
        assertEquals(2, entries.size());
        for (Object entry : entries) {
            Map<?, ?> publicKey = (Map<?, ?>) entry;
            assertEquals("RSA", publicKey.get("kty"));
            assertEquals("RS256", publicKey.get("alg"));
            assertEquals(List.of("verify"), publicKey.get("key_ops"));
            assertFalse(publicKey.containsKey("d"));
            assertFalse(publicKey.containsKey("p"));
            assertFalse(publicKey.containsKey("q"));
        }

        JwkSet fetchedKeys = Jwks.setParser()
                .json(deserializer)
                .build()
                .parse(new ByteArrayInputStream(discoveryResponse));
        Map<String, Key> verificationKeys = new HashMap<>();
        for (Jwk<?> jwk : fetchedKeys) {
            verificationKeys.put(jwk.getId(), jwk.toKey());
            assertEquals(Set.of(Jwks.OP.VERIFY), jwk.getOperations());
        }

        assertEquals(Map.of(
                "warehouse-retiring", retiringKey.getPublic(),
                "warehouse-active", activeKey.getPublic()), verificationKeys);
    }
}
