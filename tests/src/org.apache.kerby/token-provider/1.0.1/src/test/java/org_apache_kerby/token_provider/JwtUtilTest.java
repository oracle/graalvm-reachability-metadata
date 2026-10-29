/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.token_provider;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.ReadOnlyJWTClaimsSet;
import org.apache.kerby.kerberos.provider.token.JwtUtil;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class JwtUtilTest {
    @Test
    void copiesReadOnlyClaimsIntoAnIndependentlyEditableClaimSet() {
        JWTClaimsSet original = new JWTClaimsSet();
        original.setSubject("alice");
        original.setIssuer("issuer");
        original.setAudience(List.of("service"));
        original.setExpirationTime(new Date(1700003600000L));
        original.setJWTID("request-token");
        original.setCustomClaim("scope", "read:mail");
        ReadOnlyJWTClaimsSet readOnly = original;

        JWTClaimsSet copy = JwtUtil.from(readOnly);
        assertNotSame(original, copy);
        assertEquals(original.getAllClaims(), copy.getAllClaims());

        copy.setSubject("bob");
        copy.setCustomClaim("scope", "write:mail");
        assertEquals("alice", original.getSubject());
        assertEquals("read:mail", original.getAllClaims().get("scope"));
        assertEquals("bob", copy.getSubject());
        assertEquals("write:mail", copy.getAllClaims().get("scope"));
    }
}
