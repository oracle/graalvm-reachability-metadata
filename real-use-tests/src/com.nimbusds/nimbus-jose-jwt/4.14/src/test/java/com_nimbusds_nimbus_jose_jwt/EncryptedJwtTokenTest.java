/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_nimbusds_nimbus_jose_jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.crypto.PasswordBasedDecrypter;
import com.nimbusds.jose.crypto.PasswordBasedEncrypter;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises encrypted JWT creation and password-based decryption for a service carrying private claims.
public class EncryptedJwtTokenTest {
    @Test
    @Timeout(60)
    public void encryptsAndDecryptsPrivateServiceToken() throws Exception {
        String password = "service-password-for-orders-42";
        JWEHeader header = new JWEHeader.Builder(
                JWEAlgorithm.PBES2_HS256_A128KW, EncryptionMethod.A128CBC_HS256)
                .type(JOSEObjectType.JWT)
                .contentType("application/jwt")
                .keyID("orders-token-password")
                .build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://orders.example.invalid")
                .subject("order-42")
                .audience(Collections.singletonList("fulfillment-service"))
                .claim("shippingAddress", "42 Example Street")
                .claim("priority", "overnight")
                .build();

        EncryptedJWT token = new EncryptedJWT(header, claims);
        token.encrypt(new PasswordBasedEncrypter(password, 8, 1000));
        String serializedToken = token.serialize();

        assertFalse(serializedToken.contains("42 Example Street"));

        EncryptedJWT receivedToken = EncryptedJWT.parse(serializedToken);
        receivedToken.decrypt(new PasswordBasedDecrypter(password));
        JWTClaimsSet receivedClaims = receivedToken.getJWTClaimsSet();

        assertEquals(JOSEObjectType.JWT, receivedToken.getHeader().getType());
        assertEquals(JWEAlgorithm.PBES2_HS256_A128KW, receivedToken.getHeader().getAlgorithm());
        assertEquals(EncryptionMethod.A128CBC_HS256, receivedToken.getHeader().getEncryptionMethod());
        assertEquals("orders-token-password", receivedToken.getHeader().getKeyID());
        assertEquals("order-42", receivedClaims.getSubject());
        assertEquals("fulfillment-service", receivedClaims.getAudience().get(0));
        assertEquals("42 Example Street", receivedClaims.getStringClaim("shippingAddress"));
        assertEquals("overnight", receivedClaims.getStringClaim("priority"));
    }
}
