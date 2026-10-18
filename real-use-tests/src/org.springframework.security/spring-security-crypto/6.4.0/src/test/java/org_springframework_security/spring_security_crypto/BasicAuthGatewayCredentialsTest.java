/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.codec.Base64;
import org.springframework.security.crypto.codec.Utf8;

/**
 * Base64 text framing through the `codec.Base64` encoder, decoder, and `isBase64` validator.
 *
 * An API gateway issues HTTP Basic `Authorization` headers for the partner services it
 * calls and parses the headers its own clients send. It validates every header payload
 * with `Base64.isBase64` before decoding, rejects malformed or tampered headers instead
 * of crashing, keeps non-ASCII passwords intact, and exchanges binary signing keys with
 * partners whose tooling uses the JDK Base64 encoder.
 */
public class BasicAuthGatewayCredentialsTest {

    @Test
    void encodesTheRfc4648ReferenceVectors() {
        Map<String, String> vectors = new LinkedHashMap<>();
        vectors.put("", "");
        vectors.put("f", "Zg==");
        vectors.put("fo", "Zm8=");
        vectors.put("foo", "Zm9v");
        vectors.put("foob", "Zm9vYg==");
        vectors.put("fooba", "Zm9vYmE=");
        vectors.put("foobar", "Zm9vYmFy");

        for (Map.Entry<String, String> vector : vectors.entrySet()) {
            byte[] encoded = Base64.encode(Utf8.encode(vector.getKey()));
            assertEquals(vector.getValue(), Utf8.decode(encoded), vector.getKey());
            assertEquals(vector.getKey(), Utf8.decode(Base64.decode(encoded)), vector.getValue());
        }
    }

    @Test
    void issuesAndParsesBasicHeadersForPartnerServices() {
        BasicCredentialsCodec codec = new BasicCredentialsCodec();

        String header = codec.header(new Credentials("Aladdin", "open sesame"));
        assertEquals("Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==", header, "the RFC 7617 example");

        Credentials parsed = codec.parse(header).orElseThrow();
        assertEquals("Aladdin", parsed.username());
        assertEquals("open sesame", parsed.password());

        Credentials withColon = codec.parse(codec.header(new Credentials("billing", "a:b:c")))
                .orElseThrow();
        assertEquals("billing", withColon.username());
        assertEquals("a:b:c", withColon.password(), "only the first colon splits the pair");
    }

    @Test
    void keepsNonAsciiPasswordsIntactAcrossTheHeader() {
        BasicCredentialsCodec codec = new BasicCredentialsCodec();
        Credentials original = new Credentials("j\u00fcrgen",
                "gr\u00fc\u00dfe-\u20ac-\u65e5\u672c");

        String header = codec.header(original);
        String payload = header.substring("Basic ".length());
        assertEquals(java.util.Base64.getEncoder().encodeToString(
                "j\u00fcrgen:gr\u00fc\u00dfe-\u20ac-\u65e5\u672c".getBytes(StandardCharsets.UTF_8)),
                payload, "the payload is the UTF-8 bytes of user:password");

        assertEquals(original, codec.parse(header).orElseThrow());
    }

    @Test
    void rejectsMalformedHeadersBeforeDecoding() {
        BasicCredentialsCodec codec = new BasicCredentialsCodec();

        assertTrue(Base64.isBase64(Utf8.encode("QWxhZGRpbjpvcGVuIHNlc2FtZQ==")));
        assertFalse(Base64.isBase64(Utf8.encode("QWxh*GRp")), "'*' is outside the alphabet");
        assertEquals("Aladdin:open sesam",
                Utf8.decode(Base64.decode(Utf8.encode("QWxhZGRpbjpvcGVuIHNlc2FtZQ"))),
                "the codec drops a trailing partial quartet, so the gateway checks padding first");
        assertEquals("foobar", Utf8.decode(Base64.decode(Utf8.encode("Zm9v\nYmFy"))),
                "line breaks inside a payload are skipped");

        assertEquals(Optional.empty(), codec.parse("Bearer QWxhZGRpbjpvcGVuIHNlc2FtZQ=="));
        assertEquals(Optional.empty(), codec.parse("Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ"));
        assertEquals(Optional.empty(), codec.parse("Basic QWxh*GRpbjpvcGVuIHNlc2FtZQ=="));
        assertEquals(Optional.empty(), codec.parse("Basic Zm8"),
                "short payloads never reach the codec");
        assertEquals(Optional.empty(), codec.parse("Basic "));
        assertEquals(Optional.empty(), codec.parse("Basic " + Utf8.decode(Base64.encode(
                Utf8.encode("no-separator")))), "a payload without a colon is not a credential");
    }

    @Test
    void exchangesBinarySigningKeysWithJdkEncodedPartners() {
        byte[] signingKey = new byte[256];
        for (int i = 0; i < signingKey.length; i++) {
            signingKey[i] = (byte) i;
        }

        String ours = Utf8.decode(Base64.encode(signingKey));
        assertEquals(java.util.Base64.getEncoder().encodeToString(signingKey), ours,
                "the gateway's encoding matches the partner's JDK encoding byte for byte");
        assertFalse(ours.contains("\n"), "encoded keys stay on one line");

        byte[] partnerKey = Utf8.encode(java.util.Base64.getEncoder().encodeToString(signingKey));
        assertTrue(Base64.isBase64(partnerKey));
        assertArrayEquals(signingKey, Base64.decode(partnerKey));
    }

    /** A username and password pair carried by an HTTP Basic header. */
    record Credentials(String username, String password) {
    }

    /**
     * Builds and parses `Authorization: Basic` values for the gateway. The codec tolerates
     * missing padding, so the parser requires whole quartets before asking `isBase64`.
     */
    static final class BasicCredentialsCodec {

        private static final String SCHEME = "Basic ";

        String header(Credentials credentials) {
            byte[] pair = Utf8.encode(credentials.username() + ":" + credentials.password());
            return SCHEME + Utf8.decode(Base64.encode(pair));
        }

        Optional<Credentials> parse(String header) {
            if (!header.startsWith(SCHEME)) {
                return Optional.empty();
            }
            byte[] payload = Utf8.encode(header.substring(SCHEME.length()).trim());
            if (payload.length == 0 || payload.length % 4 != 0 || !Base64.isBase64(payload)) {
                return Optional.empty();
            }
            String pair = Utf8.decode(Base64.decode(payload));
            int separator = pair.indexOf(':');
            if (separator < 0) {
                return Optional.empty();
            }
            return Optional.of(new Credentials(pair.substring(0, separator),
                    pair.substring(separator + 1)));
        }
    }
}
