/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_nimbusds_nimbus_jose_jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTClaimsVerifier;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises remote JWK retrieval and JWT processing for an API authentication boundary.
public class RemoteJwkJwtProcessorTest {
    @Test
    @Timeout(60)
    public void processesSignedTokenWithRemoteVerificationKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAKey signingKey = new RSAKey.Builder(publicKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID("orders-signing-key")
                .build();
        JWKSet publishedKeys = new JWKSet(signingKey);
        HttpServer keyServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        keyServer.createContext("/oauth/jwks", exchange ->
                writeJson(exchange, publishedKeys.toJSONObject().toString()));
        keyServer.start();

        try {
            URL keySetUrl = new URL(
                    "http://127.0.0.1:" + keyServer.getAddress().getPort() + "/oauth/jwks");
            RemoteJWKSet<SecurityContext> keySource = new RemoteJWKSet<>(
                    keySetUrl, new DefaultResourceRetriever(10_000, 10_000, 1_048_576));
            DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));
            processor.setJWTClaimsVerifier(new TenantClaimsVerifier(
                    "https://accounts.example.invalid", "orders-api"));

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer("https://accounts.example.invalid")
                    .subject("customer-42")
                    .audience(Collections.singletonList("orders-api"))
                    .claim("scope", "orders:read")
                    .build();
            SignedJWT token = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID("orders-signing-key")
                            .build(),
                    claims);
            token.sign(new RSASSASigner(keyPair.getPrivate()));

            JWTClaimsSet result = processor.process(token.serialize(), null);

            assertEquals("customer-42", result.getSubject());
            assertEquals("orders:read", result.getStringClaim("scope"));
        } finally {
            keyServer.stop(0);
        }
    }

    private static void writeJson(HttpExchange exchange, String json) throws IOException {
        try (exchange) {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/jwk-set+json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private static final class TenantClaimsVerifier implements JWTClaimsVerifier {
        private final String expectedIssuer;
        private final String expectedAudience;

        private TenantClaimsVerifier(String expectedIssuer, String expectedAudience) {
            this.expectedIssuer = expectedIssuer;
            this.expectedAudience = expectedAudience;
        }

        @Override
        public void verify(JWTClaimsSet claims) throws BadJWTException {
            if (!expectedIssuer.equals(claims.getIssuer())
                    || !claims.getAudience().contains(expectedAudience)) {
                throw new BadJWTException("Token is not intended for this API");
            }
        }
    }
}
