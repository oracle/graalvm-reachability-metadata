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
 * Exercises XZ's concatenated stream decoding subsystem for rotated application snapshots.
 */
public class ConcatenatedSnapshotArchiveTest {
    @Test
    void restoresSnapshotsStoredAsConcatenatedXzStreams() throws IOException {
        byte[] morningSnapshot = snapshot("morning", 96);
        byte[] eveningSnapshot = snapshot("evening", 96);
        byte[] morningArchive = compress(morningSnapshot);
        byte[] eveningArchive = compress(eveningSnapshot);
        ByteArrayOutputStream concatenatedArchive = new ByteArrayOutputStream();
        concatenatedArchive.write(morningArchive);
        concatenatedArchive.write(eveningArchive);

        byte[] archiveBytes = concatenatedArchive.toByteArray();
        assertThat(archiveBytes).startsWith(XZ.HEADER_MAGIC);
        assertThat(archiveBytes).containsSequence(XZ.HEADER_MAGIC);

        try (XZInputStream archive = new XZInputStream(new ByteArrayInputStream(archiveBytes))) {
            assertThat(archive.readAllBytes()).containsExactly(concatenate(morningSnapshot, eveningSnapshot));
        }
    }

    private static byte[] compress(byte[] snapshot) throws IOException {
        LZMA2Options options = new LZMA2Options(1);
        options.setDictSize(1 << 16);
        ByteArrayOutputStream archiveBytes = new ByteArrayOutputStream();
        try (XZOutputStream archive = new XZOutputStream(archiveBytes, options, XZ.CHECK_CRC64)) {
            archive.write(snapshot);
        }
        return archiveBytes.toByteArray();
    }

    private static byte[] snapshot(String period, int recordCount) {
        StringBuilder snapshot = new StringBuilder();
        for (int record = 0; record < recordCount; record++) {
            snapshot.append("period=").append(period)
                    .append(" node=").append(record % 12)
                    .append(" state=ready revision=").append(record % 6)
                    .append('\n');
        }
        return snapshot.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
