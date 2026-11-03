/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Tika's content type detection subsystem for uploaded documents.
public class DocumentUploadTypeDetectionTest {
    @Test
    @Timeout(60)
    public void identifiesXmlFromContentAndTheOriginalFileName() throws IOException {
        Tika tika = new Tika();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "receipt.xml");
        byte[] receipt = "<receipt><id>R-1042</id><total>19.95</total></receipt>"
                .getBytes(StandardCharsets.UTF_8);

        String detectedType;
        try (ByteArrayInputStream upload = new ByteArrayInputStream(receipt)) {
            detectedType = tika.detect(upload, metadata);
        }

        assertEquals("application/xml", detectedType);
        assertEquals("application/xml", tika.detect("receipt.xml"));
    }
}
