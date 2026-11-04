/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_nimbusds_nimbus_jose_jwt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises standard JWT temporal claim validation for an API access token.
public class JwtTemporalClaimsValidationTest {
    @Test
    @Timeout(60)
    public void acceptsCurrentTokenAndRejectsExpiredToken() {
        long now = System.currentTimeMillis();
        DefaultJWTClaimsVerifier verifier = new DefaultJWTClaimsVerifier();
        verifier.setMaxClockSkew(0);

        JWTClaimsSet currentToken = new JWTClaimsSet.Builder()
                .issuer("https://accounts.example.invalid")
                .subject("customer-42")
                .notBeforeTime(new Date(now - 60_000))
                .expirationTime(new Date(now + 60_000))
                .build();

        assertDoesNotThrow(() -> verifier.verify(currentToken));
        assertEquals("customer-42", currentToken.getSubject());

        JWTClaimsSet expiredToken = new JWTClaimsSet.Builder()
                .issuer("https://accounts.example.invalid")
                .subject("customer-42")
                .expirationTime(new Date(0))
                .build();

        BadJWTException exception = assertThrows(
                BadJWTException.class,
                () -> verifier.verify(expiredToken));
        assertEquals("Expired JWT", exception.getMessage());
    }
}
