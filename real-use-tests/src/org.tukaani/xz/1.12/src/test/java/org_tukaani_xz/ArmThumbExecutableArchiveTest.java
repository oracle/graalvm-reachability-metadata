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

import org.junit.jupiter.api.Test;
import org.tukaani.xz.ARMThumbOptions;
import org.tukaani.xz.FilterOptions;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

/**
 * Exercises XZ's ARM Thumb branch conversion subsystem for compressed mobile executable archives.
 */
public class ArmThumbExecutableArchiveTest {
    @Test
    void archivesAndRestoresArmThumbExecutableCode() throws IOException {
        byte[] executable = executablePayload();
        ARMThumbOptions branchFilter = new ARMThumbOptions();
        branchFilter.setStartOffset(0x2000);
        LZMA2Options compression = new LZMA2Options(1);
        compression.setDictSize(1 << 16);
        FilterOptions[] filters = {branchFilter, compression};

        ByteArrayOutputStream archiveBytes = new ByteArrayOutputStream();
        try (XZOutputStream archive = new XZOutputStream(archiveBytes, filters, XZ.CHECK_SHA256)) {
            archive.write(executable);
        }

        byte[] compressedArchive = archiveBytes.toByteArray();
        assertThat(compressedArchive).startsWith(XZ.HEADER_MAGIC);
        assertThat(compressedArchive.length).isLessThan(executable.length);

        try (XZInputStream archive = new XZInputStream(new ByteArrayInputStream(compressedArchive))) {
            assertThat(archive.readAllBytes()).containsExactly(executable);
        }
    }

    private static byte[] executablePayload() {
        byte[] executable = new byte[256 * 16];
        for (int instruction = 0; instruction < executable.length; instruction += 4) {
            executable[instruction] = 0;
            executable[instruction + 1] = (byte) 0xf0;
            executable[instruction + 2] = 0;
            executable[instruction + 3] = (byte) 0xf8;
        }
        return executable;
    }
}
