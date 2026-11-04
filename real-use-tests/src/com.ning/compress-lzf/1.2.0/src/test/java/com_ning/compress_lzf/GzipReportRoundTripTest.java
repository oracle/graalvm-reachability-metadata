/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ning.compress_lzf;

import static org.assertj.core.api.Assertions.assertThat;

import com.ning.compress.gzip.OptimizedGZIPInputStream;
import com.ning.compress.gzip.OptimizedGZIPOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The gzip compression subsystem is exercised by storing and reading a report.
 */
public class GzipReportRoundTripTest {
    @Test
    public void compressesAndRestoresAnApplicationReport() throws IOException {
        byte[] report = applicationReport();
        byte[] compressed = compress(report);

        assertThat(compressed).startsWith((byte) 0x1f, (byte) 0x8b);
        assertThat(compressed).hasSizeLessThan(report.length);

        assertThat(decompress(compressed)).containsExactly(report);
    }

    private static byte[] compress(byte[] report) throws IOException {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (OptimizedGZIPOutputStream gzip = new OptimizedGZIPOutputStream(encoded)) {
            gzip.write(report);
        }
        return encoded.toByteArray();
    }

    private static byte[] decompress(byte[] compressed) throws IOException {
        ByteArrayOutputStream restored = new ByteArrayOutputStream();
        try (OptimizedGZIPInputStream gzip = new OptimizedGZIPInputStream(
                new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[113];
            int read;
            while ((read = gzip.read(buffer)) != -1) {
                restored.write(buffer, 0, read);
            }
        }
        return restored.toByteArray();
    }

    private static byte[] applicationReport() {
        StringBuilder report = new StringBuilder();
        for (int event = 0; event < 2048; event++) {
            report.append("event=")
                    .append(event)
                    .append(" status=processed tenant=warehouse-7\n");
        }
        return report.toString().getBytes(StandardCharsets.UTF_8);
    }
}
