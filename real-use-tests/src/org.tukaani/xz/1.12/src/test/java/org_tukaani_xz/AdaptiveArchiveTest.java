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
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

/**
 * Exercises XZ's block filter reconfiguration subsystem for changing archive workloads.
 */
public class AdaptiveArchiveTest {
    @Test
    void restoresAnArchiveWrittenWithChangingCompressionOptions() throws IOException {
        byte[] manifest = repeated("manifest: service=orders status=ready\n", 96);
        byte[] records = repeated("order=1001 state=packed\n", 128);
        byte[] expected = concatenate(manifest, records);
        ByteArrayOutputStream archiveBytes = new ByteArrayOutputStream();

        try (XZOutputStream archive = new XZOutputStream(archiveBytes, options(1), XZ.CHECK_CRC64)) {
            archive.write(manifest);
            archive.endBlock();
            archive.updateFilters(options(2));
            archive.write(records);
        }

        byte[] compressedArchive = archiveBytes.toByteArray();
        assertThat(compressedArchive).startsWith(XZ.HEADER_MAGIC);

        try (XZInputStream archive = new XZInputStream(new ByteArrayInputStream(compressedArchive))) {
            assertThat(archive.readAllBytes()).containsExactly(expected);
        }
    }

    private static LZMA2Options options(int preset) throws IOException {
        LZMA2Options options = new LZMA2Options(preset);
        options.setDictSize(1 << 16);
        return options;
    }

    private static byte[] repeated(String value, int count) {
        byte[] unit = value.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[unit.length * count];
        for (int offset = 0; offset < result.length; offset += unit.length) {
            System.arraycopy(unit, 0, result, offset, unit.length);
        }
        return result;
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
