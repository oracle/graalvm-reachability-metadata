/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tukaani_xz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.MemoryLimitException;
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

/**
 * Exercises XZ's memory-bounded decompression subsystem for an application archive service.
 */
public class MemoryBoundedDecompressionTest {
    @Test
    void rejectsArchivesAboveTheConfiguredMemoryBudgetAndReadsWithinIt() throws IOException {
        byte[] manifest = manifest();
        byte[] compressedManifest = compress(manifest);

        MemoryLimitException rejection = assertThrows(MemoryLimitException.class, () -> {
            try (XZInputStream input = new XZInputStream(
                    new ByteArrayInputStream(compressedManifest), 1)) {
                input.readAllBytes();
            }
        });
        assertThat(rejection.getMemoryNeeded()).isGreaterThan(rejection.getMemoryLimit());

        try (XZInputStream input = new XZInputStream(
                new ByteArrayInputStream(compressedManifest), 256)) {
            assertThat(input.readAllBytes()).containsExactly(manifest);
        }
    }

    private static byte[] compress(byte[] manifest) throws IOException {
        LZMA2Options options = new LZMA2Options(1);
        options.setDictSize(1 << 16);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (XZOutputStream output = new XZOutputStream(compressed, options, XZ.CHECK_CRC32)) {
            output.write(manifest);
        }
        return compressed.toByteArray();
    }

    private static byte[] manifest() {
        StringBuilder manifest = new StringBuilder();
        for (int entry = 0; entry < 240; entry++) {
            manifest.append("artifact-").append(entry % 12)
                    .append(";version=2026.01;status=available;region=north\n");
        }
        return manifest.toString().getBytes(StandardCharsets.UTF_8);
    }
}
