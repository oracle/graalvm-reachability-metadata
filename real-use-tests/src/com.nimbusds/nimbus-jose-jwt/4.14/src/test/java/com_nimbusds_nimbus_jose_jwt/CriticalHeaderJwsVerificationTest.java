/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_nimbusds_nimbus_jose_jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises critical JWS header validation for a tenant-aware API request.
public class CriticalHeaderJwsVerificationTest {
    @Test
    @Timeout(60)
    public void verifiesRequestOnlyAfterAcknowledgingCriticalTenantHeader() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        String tenantHeader = "tenant";
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID("api-signing-key")
                .customParam(tenantHeader, "tenant-42")
                .criticalParams(Collections.singleton(tenantHeader))
                .build();
        JWSObject request = new JWSObject(header, new Payload("GET /orders/42"));
        request.sign(new RSASSASigner(keyPair.getPrivate()));

        JWSObject receivedRequest = JWSObject.parse(request.serialize());
        RSASSAVerifier unconfiguredVerifier = new RSASSAVerifier(
                (RSAPublicKey) keyPair.getPublic());
        assertFalse(receivedRequest.verify(unconfiguredVerifier));

        RSASSAVerifier tenantAwareVerifier = new RSASSAVerifier(
                (RSAPublicKey) keyPair.getPublic(), Collections.singleton(tenantHeader));
        assertTrue(receivedRequest.verify(tenantAwareVerifier));
        assertEquals(Collections.singleton(tenantHeader),
                receivedRequest.getHeader().getCriticalParams());
        assertEquals("tenant-42", receivedRequest.getHeader().getCustomParam(tenantHeader));
        assertEquals("GET /orders/42", receivedRequest.getPayload().toString());
    }
}
