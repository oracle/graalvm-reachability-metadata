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
import org.tukaani.xz.X86Options;
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

/**
 * Exercises XZ's x86 branch conversion subsystem for compressed executable archives.
 */
public class X86ExecutableArchiveTest {
    @Test
    void archivesExecutableCodeWithAnX86BranchFilter() throws IOException {
        byte[] executable = executablePayload();
        X86Options branchFilter = new X86Options();
        branchFilter.setStartOffset(0x1000);
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
        for (int instruction = 0; instruction < 256; instruction++) {
            int offset = instruction * 16;
            executable[offset] = (byte) 0xE8;
            int relativeTarget = (instruction % 8) * 64 - (offset + 5);
            executable[offset + 1] = (byte) relativeTarget;
            executable[offset + 2] = (byte) (relativeTarget >>> 8);
            executable[offset + 3] = (byte) (relativeTarget >>> 16);
            executable[offset + 4] = (byte) (relativeTarget >>> 24);
            for (int padding = 5; padding < 16; padding++) {
                executable[offset + padding] = (byte) 0x90;
            }
        }
        return executable;
    }
}
