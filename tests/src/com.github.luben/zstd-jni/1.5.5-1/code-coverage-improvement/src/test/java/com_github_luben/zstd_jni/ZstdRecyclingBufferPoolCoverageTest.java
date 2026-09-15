/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.RecyclingBufferPool;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdInputStreamNoFinalizer;
import com.github.luben.zstd.ZstdOutputStream;
import com.github.luben.zstd.ZstdOutputStreamNoFinalizer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ZstdRecyclingBufferPoolCoverageTest {
    private static final byte[] INPUT = ("recycling buffer pools are used by public stream constructors; "
            + "repeated records make the compression and decompression paths do useful work. ").repeat(12)
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void finalizingStreamsUseThePublicRecyclingPoolEntry() throws IOException {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (ZstdOutputStream output = new ZstdOutputStream(encoded, RecyclingBufferPool.INSTANCE)) {
            output.write(INPUT);
        }

        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        try (ZstdInputStream input = new ZstdInputStream(
                new ByteArrayInputStream(encoded.toByteArray()), RecyclingBufferPool.INSTANCE)) {
            copy(input, decoded);
        }

        assertThat(decoded.toByteArray()).isEqualTo(INPUT);
    }

    @Test
    void noFinalizerStreamsUseThePublicRecyclingPoolEntry() throws IOException {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (ZstdOutputStreamNoFinalizer output = new ZstdOutputStreamNoFinalizer(
                encoded, RecyclingBufferPool.INSTANCE)) {
            output.write(INPUT);
        }

        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        try (ZstdInputStreamNoFinalizer input = new ZstdInputStreamNoFinalizer(
                new ByteArrayInputStream(encoded.toByteArray()), RecyclingBufferPool.INSTANCE)) {
            copy(input, decoded);
        }

        assertThat(decoded.toByteArray()).isEqualTo(INPUT);
    }

    private static void copy(ZstdInputStream input, ByteArrayOutputStream output) throws IOException {
        byte[] buffer = new byte[257];
        int read;
        while ((read = input.read(buffer, 0, buffer.length)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    private static void copy(ZstdInputStreamNoFinalizer input, ByteArrayOutputStream output) throws IOException {
        byte[] buffer = new byte[257];
        int read;
        while ((read = input.read(buffer, 0, buffer.length)) != -1) {
            output.write(buffer, 0, read);
        }
    }
}
