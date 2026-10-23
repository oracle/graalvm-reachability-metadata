/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_jackson;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DeserializationException;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jackson's binary claim encoding for document-transfer checksum receipts.
public class BinaryDigestReceiptTest {
    @Test
    @Timeout(60)
    public void restoresDigestBytesAndDetectsChangedDocumentContent() throws NoSuchAlgorithmException {
        byte[] document = "Invoice INV-704\nAmount: USD 125.00\n".getBytes(StandardCharsets.UTF_8);
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] expectedDigest = sha256.digest(document);
        Claims receipt = Jwts.claims()
                .issuer("document-transfer")
                .subject("invoice-704")
                .add("digest_algorithm", "SHA-256")
                .add("digest", expectedDigest)
                .add("content_length", document.length)
                .build();
        JacksonSerializer<Map<String, ?>> serializer = new JacksonSerializer<>();
        byte[] response = serializer.serialize(receipt);

        // A JSON client receives a base64 string, not a lossy array of signed byte values.
        JacksonDeserializer<Map<String, ?>> jsonClient = new JacksonDeserializer<>();
        Map<String, ?> wireReceipt = jsonClient.deserialize(response);
        assertEquals(Base64.getEncoder().encodeToString(expectedDigest), wireReceipt.get("digest"));

        JacksonDeserializer<Map<String, ?>> receiptDecoder = new JacksonDeserializer<>(
                Map.of("digest", byte[].class));
        Claims received = Jwts.claims().add(receiptDecoder.deserialize(response)).build();
        byte[] receivedDigest = received.get("digest", byte[].class);

        assertEquals("document-transfer", received.getIssuer());
        assertEquals("invoice-704", received.getSubject());
        assertEquals("SHA-256", received.get("digest_algorithm", String.class));
        assertEquals(document.length, received.get("content_length", Integer.class));
        assertArrayEquals(expectedDigest, receivedDigest);
        assertTrue(MessageDigest.isEqual(receivedDigest, sha256.digest(document)));

        byte[] alteredDocument = "Invoice INV-704\nAmount: USD 925.00\n".getBytes(StandardCharsets.UTF_8);
        assertEquals(document.length, alteredDocument.length);
        assertFalse(MessageDigest.isEqual(receivedDigest, sha256.digest(alteredDocument)));

        String malformedReceipt = """
                {
                  "iss": "document-transfer",
                  "sub": "invoice-704",
                  "digest_algorithm": "SHA-256",
                  "digest": "not!base64"
                }
                """;
        assertThrows(DeserializationException.class,
                () -> receiptDecoder.deserialize(malformedReceipt.getBytes(StandardCharsets.UTF_8)));
    }
}
