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
import org.tukaani.xz.FilterOptions;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.RISCVOptions;
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

/**
 * Exercises XZ's RISC-V branch conversion subsystem for compressed embedded executable archives.
 */
public class RiscvExecutableArchiveTest {
    @Test
    void archivesAndRestoresRiscvExecutableCode() throws IOException {
        byte[] executable = executablePayload();
        RISCVOptions branchFilter = new RISCVOptions();
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
            int offset = (instruction % 16 - 8) * 4;
            int encodedJump = encodeJal(offset);
            int byteOffset = instruction * 4;
            executable[byteOffset] = (byte) encodedJump;
            executable[byteOffset + 1] = (byte) (encodedJump >>> 8);
            executable[byteOffset + 2] = (byte) (encodedJump >>> 16);
            executable[byteOffset + 3] = (byte) (encodedJump >>> 24);
        }
        return executable;
    }

    private static int encodeJal(int offset) {
        int immediate = offset & 0x001fffff;
        return 0x0000006f
                | ((immediate >>> 20) << 31)
                | (((immediate >>> 1) & 0x3ff) << 21)
                | (((immediate >>> 11) & 1) << 20)
                | (((immediate >>> 12) & 0xff) << 12);
    }
}
