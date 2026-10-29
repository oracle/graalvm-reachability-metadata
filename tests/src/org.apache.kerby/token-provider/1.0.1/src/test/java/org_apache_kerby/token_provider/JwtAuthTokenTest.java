/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.token_provider;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.ReadOnlyJWTClaimsSet;
import org.apache.kerby.kerberos.provider.token.JwtAuthToken;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JwtAuthTokenTest {
    @Test
    void storesStandardAndCustomClaims() {
        JwtAuthToken token = new JwtAuthToken();
        Date issued = new Date(1700000000000L);
        Date notBefore = new Date(1700000001000L);
        Date expiration = new Date(1700003600000L);

        token.setSubject("alice");
        token.setIssuer("identity-service");
        token.setAudiences(List.of("mail", "calendar"));
        token.setIssueTime(issued);
        token.setNotBeforeTime(notBefore);
        token.setExpirationTime(expiration);
        token.addAttribute("roles", List.of("reader", "writer"));

        assertEquals("alice", token.getSubject());
        assertEquals("identity-service", token.getIssuer());
        assertEquals(List.of("mail", "calendar"), token.getAudiences());
        assertEquals(issued, token.getIssueTime());
        assertEquals(notBefore, token.getNotBeforeTime());
        assertEquals(expiration, token.getExpiredTime());
        assertEquals(List.of("reader", "writer"), token.getAttributes().get("roles"));
        assertEquals("alice", token.getAttributes().get("sub"));
    }

    @Test
    void exposesBearerAndTokenKindFlags() {
        JwtAuthToken token = new JwtAuthToken();
        assertTrue(token.isIdToken());
        assertFalse(token.isAcToken());
        assertTrue(token.isBearerToken());
        assertFalse(token.isHolderOfKeyToken());

        token.isIdToken(false);
        token.isAcToken(true);
        assertFalse(token.isIdToken());
        assertTrue(token.isAcToken());
    }

    @Test
    void constructsFromMutableAndReadOnlyClaims() {
        JWTClaimsSet claims = new JWTClaimsSet();
        claims.setSubject("alice");
        claims.setCustomClaim("department", "engineering");
        JwtAuthToken mutable = new JwtAuthToken(claims);
        ReadOnlyJWTClaimsSet readOnly = claims;
        JwtAuthToken copied = new JwtAuthToken(readOnly);

        mutable.setSubject("bob");
        assertEquals("bob", claims.getSubject());
        assertEquals("alice", copied.getSubject());
        assertEquals("engineering", copied.getAttributes().get("department"));
        copied.setIssuer("copied-issuer");
        assertEquals("copied-issuer", copied.getIssuer());
    }
}
