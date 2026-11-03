/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Tika's managed upload stream subsystem for an application document pipeline.
public class ManagedUploadStreamTest {
    @Test
    @Timeout(60)
    public void previewsAndConsumesAnUploadWhilePreservingItsLength() throws IOException {
        byte[] upload = "invoice=INV-1042&total=19.95".getBytes(StandardCharsets.US_ASCII);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "invoice.form");

        try (TikaInputStream stream = TikaInputStream.get(upload, metadata)) {
            assertTrue(TikaInputStream.isTikaInputStream(stream));
            assertEquals(upload.length, stream.getLength());
            assertEquals(Integer.toString(upload.length), metadata.get(HttpHeaders.CONTENT_LENGTH));

            byte[] preview = new byte[7];
            assertEquals(preview.length, stream.peek(preview));
            assertArrayEquals("invoice".getBytes(StandardCharsets.US_ASCII), preview);
            assertEquals(0, stream.getPosition());

            assertArrayEquals(upload, stream.readAllBytes());
            assertEquals(upload.length, stream.getPosition());
        }
    }
}
