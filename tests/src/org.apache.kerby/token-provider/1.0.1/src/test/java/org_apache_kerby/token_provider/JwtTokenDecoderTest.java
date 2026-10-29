/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.token_provider;

import com.nimbusds.jose.JWSAlgorithm;
import org.apache.kerby.kerberos.kerb.type.base.AuthToken;
import org.apache.kerby.kerberos.provider.token.JwtAuthToken;
import org.apache.kerby.kerberos.provider.token.JwtTokenDecoder;
import org.apache.kerby.kerberos.provider.token.JwtTokenEncoder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JwtTokenDecoderTest {
    @Test
    void acceptsAnActiveTokenForOneOfTheExpectedAudiences() throws Exception {
        JwtAuthToken token = token();
        Instant now = Instant.now();
        Date issued = Date.from(now.minusSeconds(7200));
        Date notBefore = Date.from(now.minusSeconds(3600));
        Date expiration = Date.from(now.plusSeconds(3600));
        token.setIssueTime(issued);
        token.setNotBeforeTime(notBefore);
        token.setExpirationTime(expiration);
        JwtTokenDecoder decoder = new JwtTokenDecoder();
        decoder.setAudiences(List.of("calendar", "mail"));

        AuthToken decoded = decoder.decodeFromString(new JwtTokenEncoder().encodeAsString(token));
        assertNotNull(decoded);
        assertEquals("alice", decoded.getSubject());
        // JWT timestamps have second precision.
        assertEquals(issued.getTime() / 1000, decoded.getIssueTime().getTime() / 1000);
        assertEquals(notBefore.getTime() / 1000, decoded.getNotBeforeTime().getTime() / 1000);
        assertEquals(expiration.getTime() / 1000, decoded.getExpiredTime().getTime() / 1000);
    }

    @Test
    void rejectsATokenForAnUnrelatedAudience() throws Exception {
        JwtTokenDecoder decoder = new JwtTokenDecoder();
        decoder.setAudiences(List.of("payments"));
        String encoded = new JwtTokenEncoder().encodeAsString(token());
        assertNull(decoder.decodeFromString(encoded));
    }

    @Test
    void rejectsAnExpiredToken() throws Exception {
        JwtAuthToken token = token();
        token.setExpirationTime(new Date(0));
        String encoded = new JwtTokenEncoder().encodeAsString(token);
        assertNull(new JwtTokenDecoder().decodeFromString(encoded));
    }

    @Test
    void rejectsATokenThatIsNotYetValid() throws Exception {
        JwtAuthToken token = token();
        token.setNotBeforeTime(Date.from(Instant.now().plusSeconds(86400)));
        String encoded = new JwtTokenEncoder().encodeAsString(token);
        assertNull(new JwtTokenDecoder().decodeFromString(encoded));
    }

    @Test
    void rejectsASignatureMadeWithADifferentKey() throws Exception {
        JwtTokenEncoder encoder = new JwtTokenEncoder();
        encoder.setJwsAlgorithm(JWSAlgorithm.HS256);
        encoder.setSignKey("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        JwtTokenDecoder decoder = new JwtTokenDecoder();
        decoder.setVerifyKey("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8));

        assertNull(decoder.decodeFromString(encoder.encodeAsString(token())));
        assertFalse(decoder.isSigned());
    }

    @Test
    void reportsMalformedCompactTokensAsIoExceptions() {
        JwtTokenDecoder decoder = new JwtTokenDecoder();
        assertThrows(IOException.class, () -> decoder.decodeFromString("not-a-jwt"));
        assertThrows(IOException.class,
                () -> decoder.decodeFromBytes("not-a-jwt".getBytes(StandardCharsets.UTF_8)));
    }

    private static JwtAuthToken token() {
        JwtAuthToken token = new JwtAuthToken();
        token.setSubject("alice");
        token.setAudiences(List.of("mail", "storage"));
        return token;
    }
}
