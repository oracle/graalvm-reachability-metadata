/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ning.compress_lzf;

import static org.assertj.core.api.Assertions.assertThat;

import com.ning.compress.lzf.LZFInputStream;
import com.ning.compress.lzf.parallel.PLZFOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;

/**
 * The parallel compression subsystem is exercised by compressing an application export file.
 */
public class ParallelFileCompressionTest {
    @Test
    public void compressesAndRestoresAnApplicationExport() throws IOException {
        byte[] export = applicationExport();
        Path source = Files.createTempFile("compress-lzf-export-", ".csv");
        try {
            Files.write(source, export);
            ByteArrayOutputStream encodedBytes = new ByteArrayOutputStream();
            try (FileChannel input = FileChannel.open(source, StandardOpenOption.READ);
                    PLZFOutputStream compressed = new PLZFOutputStream(encodedBytes)) {
                compressed.write(input);
                assertThat(compressed.isOpen()).isTrue();
            }

            byte[] encoded = encodedBytes.toByteArray();
            assertThat(encoded).hasSizeLessThan(export.length);
            assertThat(restore(encoded)).containsExactly(export);
        } finally {
            Files.deleteIfExists(source);
        }
    }

    private static byte[] restore(byte[] encoded) throws IOException {
        ByteArrayOutputStream restored = new ByteArrayOutputStream();
        try (LZFInputStream compressed = new LZFInputStream(
                new ByteArrayInputStream(encoded), true)) {
            byte[] buffer = new byte[431];
            int read;
            while ((read = compressed.read(buffer)) != -1) {
                restored.write(buffer, 0, read);
            }
        }
        return restored.toByteArray();
    }

    private static byte[] applicationExport() {
        StringBuilder export = new StringBuilder();
        for (int row = 0; row < 12000; row++) {
            export.append("account-")
                    .append(row % 97)
                    .append(",warehouse-7,processed,2025-01-15T10:00:00Z\n");
        }
        return export.toString().getBytes(StandardCharsets.UTF_8);
    }
}
