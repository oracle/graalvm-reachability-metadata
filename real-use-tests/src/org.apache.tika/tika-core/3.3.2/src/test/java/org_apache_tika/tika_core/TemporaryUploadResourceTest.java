/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Tika's temporary resource lifecycle subsystem for staging an uploaded document.
public class TemporaryUploadResourceTest {
    @Test
    @Timeout(60)
    public void stagesAnUploadAndRemovesItAfterProcessing() throws IOException {
        byte[] upload = "customer=Acme;invoice=INV-1042".getBytes(StandardCharsets.US_ASCII);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "invoice.form");

        Path stagedUpload;
        try (TemporaryResources resources = new TemporaryResources()) {
            stagedUpload = resources.createTempFile(metadata);
            Files.write(stagedUpload, upload);

            assertTrue(Files.exists(stagedUpload));
            assertArrayEquals(upload, Files.readAllBytes(stagedUpload));
        }

        assertFalse(Files.exists(stagedUpload));
    }
}
