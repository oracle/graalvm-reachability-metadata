/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import org.apache.tika.io.BoundedInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Tika's bounded input stream subsystem for limiting an uploaded document.
public class BoundedUploadReadTest {
    @Test
    @Timeout(60)
    public void limitsAnUploadBeforeApplicationProcessesItsContent() throws IOException {
        byte[] upload = "customer=Acme;notes=contains-private-details".getBytes(StandardCharsets.US_ASCII);
        int maximumBytes = 20;

        try (BoundedInputStream bounded = new BoundedInputStream(maximumBytes,
                new ByteArrayInputStream(upload))) {
            ByteArrayOutputStream acceptedContent = new ByteArrayOutputStream();

            long copiedBytes = bounded.transferTo(acceptedContent);

            assertEquals(maximumBytes, copiedBytes);
            assertEquals("customer=Acme;notes=", acceptedContent.toString(StandardCharsets.US_ASCII));
            assertEquals(maximumBytes, bounded.getPos());
            assertTrue(bounded.hasHitBound());
        }
    }
}
