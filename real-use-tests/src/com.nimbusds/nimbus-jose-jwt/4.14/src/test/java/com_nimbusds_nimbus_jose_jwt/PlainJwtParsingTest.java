/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_nimbusds_nimbus_jose_jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.PlainJWT;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises plain JWT parsing for an internal service carrying trusted metadata.
public class PlainJwtParsingTest {
    @Test
    @Timeout(60)
    public void parsesInternalServiceMetadataToken() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://orders.example.invalid")
                .subject("worker-42")
                .audience(Collections.singletonList("inventory-service"))
                .claim("region", "eu-west-1")
                .claim("batchSize", 25)
                .build();
        PlainJWT metadataToken = new PlainJWT(claims);

        String serializedToken = metadataToken.serialize();
        JWT parsedToken = JWTParser.parse(serializedToken);

        assertInstanceOf(PlainJWT.class, parsedToken);
        JWTClaimsSet parsedClaims = parsedToken.getJWTClaimsSet();
        assertEquals("https://orders.example.invalid", parsedClaims.getIssuer());
        assertEquals("worker-42", parsedClaims.getSubject());
        assertEquals("inventory-service", parsedClaims.getAudience().get(0));
        assertEquals("eu-west-1", parsedClaims.getStringClaim("region"));
        assertEquals(25, parsedClaims.getIntegerClaim("batchSize"));
    }
}
