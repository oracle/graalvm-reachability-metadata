/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.digest.InputStreamDigester;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Tika's document fingerprinting subsystem for deduplicating uploads.
public class DocumentFingerprintingTest {
    @Test
    @Timeout(60)
    public void fingerprintsAnUploadWithoutConsumingIt() throws IOException {
        byte[] upload = "invoice=INV-1042&total=19.95".getBytes(StandardCharsets.US_ASCII);
        Metadata metadata = new Metadata();
        ByteArrayInputStream stream = new ByteArrayInputStream(upload);
        InputStreamDigester digester = new InputStreamDigester(1024, "SHA-256", HexFormat.of()::formatHex);

        digester.digest(stream, metadata, new ParseContext());

        assertEquals("87353fbcd4eb8486c9e93567041434164fc99089caf84a9113259c89e9cc180a",
                metadata.get("X-TIKA:digest:SHA-256"));
        assertEquals(Integer.toString(upload.length), metadata.get(HttpHeaders.CONTENT_LENGTH));
        assertArrayEquals(upload, stream.readAllBytes());
    }
}
