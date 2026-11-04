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
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.tukaani.xz.DeltaOptions;
import org.tukaani.xz.FilterOptions;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

/**
 * Exercises XZ's delta filter subsystem for compact binary telemetry snapshots.
 */
public class DeltaTelemetryArchiveTest {
    @Test
    void storesAndRestoresTelemetryWithAConfiguredDeltaFilter() throws IOException {
        byte[] telemetry = telemetryPayload();
        ByteArrayOutputStream archiveBytes = new ByteArrayOutputStream();
        DeltaOptions delta = new DeltaOptions(4);
        LZMA2Options compression = new LZMA2Options(1);
        compression.setDictSize(1 << 16);
        FilterOptions[] filters = {delta, compression};

        try (XZOutputStream archive = new XZOutputStream(archiveBytes, filters, XZ.CHECK_CRC64)) {
            archive.write(telemetry);
        }

        byte[] compressedArchive = archiveBytes.toByteArray();
        assertThat(compressedArchive).startsWith(XZ.HEADER_MAGIC);
        assertThat(compressedArchive.length).isLessThan(telemetry.length);

        try (XZInputStream archive = new XZInputStream(new ByteArrayInputStream(compressedArchive))) {
            assertThat(archive.readAllBytes()).containsExactly(telemetry);
        }
    }

    private static byte[] telemetryPayload() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(payload)) {
            for (int sample = 0; sample < 128; sample++) {
                output.writeInt(100_000 + sample * 10);
                output.writeInt(20_000 + sample * 3);
                output.writeInt(3_000 + sample);
                output.writeInt(400 + sample * 2);
            }
        }
        return payload.toByteArray();
    }
}
