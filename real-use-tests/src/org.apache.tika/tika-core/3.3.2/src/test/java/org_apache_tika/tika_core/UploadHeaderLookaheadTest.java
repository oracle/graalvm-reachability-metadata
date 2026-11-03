/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import org.apache.tika.io.LookaheadInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Tika's upload header lookahead subsystem before an application parses a stream.
public class UploadHeaderLookaheadTest {
    @Test
    @Timeout(60)
    public void inspectsAHeaderThenRewindsTheUploadForParsing() throws IOException {
        byte[] upload = "MAGIC123;customer=Acme;invoice=INV-1042".getBytes(StandardCharsets.US_ASCII);
        ByteArrayInputStream source = new ByteArrayInputStream(upload);

        try (LookaheadInputStream lookahead = new LookaheadInputStream(source, 8)) {
            assertArrayEquals("MAGIC123".getBytes(StandardCharsets.US_ASCII),
                    lookahead.readNBytes(8));
            assertEquals(-1, lookahead.read());
        }

        assertArrayEquals(upload, source.readAllBytes());
    }
}
