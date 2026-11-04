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
import org.tukaani.xz.ARM64Options;
import org.tukaani.xz.FilterOptions;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

/**
 * Exercises XZ's ARM64 branch conversion subsystem for compressed mobile executable archives.
 */
public class Arm64ExecutableArchiveTest {
    @Test
    void archivesAndRestoresArm64ExecutableCode() throws IOException {
        byte[] executable = executablePayload();
        ARM64Options branchFilter = new ARM64Options();
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
        byte[] executable = new byte[256 * 64];
        for (int instruction = 0; instruction < executable.length / 4; instruction++) {
            int branchTarget = (instruction % 16) * 4;
            int encodedBranch = 0x14000000 | ((branchTarget - instruction * 4) / 4 & 0x03ffffff);
            int offset = instruction * 4;
            executable[offset] = (byte) encodedBranch;
            executable[offset + 1] = (byte) (encodedBranch >>> 8);
            executable[offset + 2] = (byte) (encodedBranch >>> 16);
            executable[offset + 3] = (byte) (encodedBranch >>> 24);
        }
        return executable;
    }
}
