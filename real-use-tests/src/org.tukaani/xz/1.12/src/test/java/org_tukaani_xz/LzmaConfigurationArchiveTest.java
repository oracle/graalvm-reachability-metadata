/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tukaani_xz;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.LZMAInputStream;
import org.tukaani.xz.LZMAOutputStream;

/**
 * Exercises XZ's standalone LZMA stream subsystem for compact configuration archives.
 */
public class LzmaConfigurationArchiveTest {
    @Test
    void archivesAndRestoresConfigurationWithStandaloneLzmaStreams() throws IOException {
        byte[] configuration = configurationPayload();
        LZMA2Options options = new LZMA2Options(1);
        options.setDictSize(1 << 16);

        ByteArrayOutputStream archiveBytes = new ByteArrayOutputStream();
        try (LZMAOutputStream archive = new LZMAOutputStream(archiveBytes, options, -1L)) {
            archive.write(configuration);
        }

        byte[] compressedArchive = archiveBytes.toByteArray();
        assertThat(compressedArchive.length).isLessThan(configuration.length);

        try (LZMAInputStream archive = new LZMAInputStream(new ByteArrayInputStream(compressedArchive))) {
            assertThat(archive.readAllBytes()).containsExactly(configuration);
        }
    }

    private static byte[] configurationPayload() {
        StringBuilder configuration = new StringBuilder();
        for (int service = 0; service < 160; service++) {
            configuration.append("service-").append(service % 8)
                    .append(".endpoint=https://config.internal/api\n")
                    .append("service-").append(service % 8)
                    .append(".retry-count=3\n")
                    .append("service-").append(service % 8)
                    .append(".timeout-ms=1500\n");
        }
        return configuration.toString().getBytes(StandardCharsets.UTF_8);
    }
}
