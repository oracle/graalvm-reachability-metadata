/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ning.compress_lzf;

import static org.assertj.core.api.Assertions.assertThat;

import com.ning.compress.lzf.util.LZFFileInputStream;
import com.ning.compress.lzf.util.LZFFileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The file archive subsystem is exercised by writing and restoring a compressed report.
 */
public class FileArchiveRoundTripTest {
    @Test
    public void storesAndRestoresAnApplicationArchive() throws IOException {
        byte[] report = applicationReport();
        Path archive = Files.createTempFile("compress-lzf-report-", ".lzf");

        try {
            try (LZFFileOutputStream output = new LZFFileOutputStream(archive.toFile())) {
                output.write(report);
                output.finishBlock();
                assertThat(output.isOpen()).isTrue();
            }

            assertThat(Files.size(archive)).isLessThan(report.length);

            ByteArrayOutputStream restored = new ByteArrayOutputStream();
            try (LZFFileInputStream input = new LZFFileInputStream(archive.toFile())) {
                byte[] buffer = new byte[197];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    restored.write(buffer, 0, read);
                }
            }

            assertThat(restored.toByteArray()).containsExactly(report);
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    private static byte[] applicationReport() {
        StringBuilder report = new StringBuilder();
        for (int event = 0; event < 4096; event++) {
            report.append("event=")
                    .append(event)
                    .append(" status=processed tenant=warehouse-7 source=archive\n");
        }
        return report.toString().getBytes(StandardCharsets.UTF_8);
    }
}
