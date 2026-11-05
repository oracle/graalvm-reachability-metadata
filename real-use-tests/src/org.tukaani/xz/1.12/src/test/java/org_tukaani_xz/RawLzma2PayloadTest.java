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
import org.tukaani.xz.ArrayCache;
import org.tukaani.xz.FinishableOutputStream;
import org.tukaani.xz.FinishableWrapperOutputStream;
import org.tukaani.xz.LZMA2Options;

/**
 * Exercises XZ's raw LZMA2 payload subsystem for compact application snapshots.
 */
public class RawLzma2PayloadTest {
    @Test
    void compressesAndRestoresAnApplicationSnapshotAsRawLzma2() throws IOException {
        byte[] snapshot = snapshotPayload();
        ByteArrayOutputStream encodedBytes = new ByteArrayOutputStream();
        LZMA2Options options = new LZMA2Options(1);
        options.setDictSize(1 << 16);

        FinishableOutputStream encoded = options.getOutputStream(
                new FinishableWrapperOutputStream(encodedBytes), ArrayCache.getDefaultCache());
        try (encoded) {
            encoded.write(snapshot);
            encoded.finish();
        }

        byte[] compressedSnapshot = encodedBytes.toByteArray();
        assertThat(compressedSnapshot).hasSizeLessThan(snapshot.length);

        ByteArrayOutputStream restoredBytes = new ByteArrayOutputStream();
        try (var decoded = options.getInputStream(
                new ByteArrayInputStream(compressedSnapshot), ArrayCache.getDefaultCache())) {
            byte[] buffer = new byte[37];
            int read;
            while ((read = decoded.read(buffer)) != -1) {
                restoredBytes.write(buffer, 0, read);
            }
        }

        assertThat(restoredBytes.toByteArray()).containsExactly(snapshot);
    }

    private static byte[] snapshotPayload() {
        StringBuilder snapshot = new StringBuilder();
        for (int record = 0; record < 320; record++) {
            snapshot.append("node-").append(record % 12)
                    .append("|region=north|state=ready|epoch=20260115|load=0.72\n");
        }
        return snapshot.toString().getBytes(StandardCharsets.UTF_8);
    }
}
