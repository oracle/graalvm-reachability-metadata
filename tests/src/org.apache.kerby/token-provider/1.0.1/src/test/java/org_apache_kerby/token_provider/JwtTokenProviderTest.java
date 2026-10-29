/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.token_provider;

import org.apache.kerby.kerberos.kerb.provider.TokenDecoder;
import org.apache.kerby.kerberos.kerb.provider.TokenEncoder;
import org.apache.kerby.kerberos.kerb.provider.TokenFactory;
import org.apache.kerby.kerberos.kerb.type.base.AuthToken;
import org.apache.kerby.kerberos.provider.token.JwtTokenProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JwtTokenProviderTest {
    @Test
    void createsIndependentTokensAndRoundTripsThroughProviderInterfaces() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider();
        TokenFactory factory = provider.createTokenFactory();
        AuthToken token = factory.createToken();
        AuthToken other = factory.createToken();
        assertNotSame(token, other);

        token.setSubject("alice@example.test");
        token.setIssuer("https://issuer.example.test");
        token.setAudiences(List.of("ticket-service"));
        token.addAttribute("display_name", "Al\u00efce");
        other.setSubject("bob@example.test");

        TokenEncoder encoder = provider.createTokenEncoder();
        TokenDecoder decoder = provider.createTokenDecoder();
        AuthToken decoded = decoder.decodeFromBytes(encoder.encodeAsBytes(token));

        assertEquals("alice@example.test", decoded.getSubject());
        assertEquals("https://issuer.example.test", decoded.getIssuer());
        assertEquals(List.of("ticket-service"), decoded.getAudiences());
        assertEquals("Al\u00efce", decoded.getAttributes().get("display_name"));
        assertEquals("bob@example.test", other.getSubject());
        assertTrue(decoded.isBearerToken());
        assertFalse(decoder.isSigned());
    }
}
