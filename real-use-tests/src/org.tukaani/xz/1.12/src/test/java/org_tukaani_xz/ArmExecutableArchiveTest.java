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
import org.tukaani.xz.ARMOptions;
import org.tukaani.xz.FilterOptions;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

/**
 * Exercises XZ's ARM branch conversion subsystem for compressed executable archives.
 */
public class ArmExecutableArchiveTest {
    @Test
    void archivesAndRestoresArmExecutableCode() throws IOException {
        byte[] executable = executablePayload();
        ARMOptions branchFilter = new ARMOptions();
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
        for (int instruction = 0; instruction < executable.length / 4; instruction++) {
            int target = (instruction % 16) * 4;
            int relativeTarget = target - instruction * 4;
            int encodedBranch = 0xea000000 | ((relativeTarget >> 2) & 0x00ffffff);
            int offset = instruction * 4;
            executable[offset] = (byte) encodedBranch;
            executable[offset + 1] = (byte) (encodedBranch >>> 8);
            executable[offset + 2] = (byte) (encodedBranch >>> 16);
            executable[offset + 3] = (byte) (encodedBranch >>> 24);
        }
        return executable;
    }
}
