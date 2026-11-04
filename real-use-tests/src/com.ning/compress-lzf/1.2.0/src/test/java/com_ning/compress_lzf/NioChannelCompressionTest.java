/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ning.compress_lzf;

import static org.assertj.core.api.Assertions.assertThat;

import com.ning.compress.lzf.LZFInputStream;
import com.ning.compress.lzf.LZFOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The NIO channel compression subsystem is exercised by writing an application export in buffers.
 */
public class NioChannelCompressionTest {
    @Test
    public void compressesAndRestoresAnExportUsingByteBuffers() throws IOException {
        byte[] export = applicationExport();
        ByteArrayOutputStream compressedBytes = new ByteArrayOutputStream();

        try (LZFOutputStream compressed = new LZFOutputStream(compressedBytes)) {
            for (int offset = 0; offset < export.length; offset += 257) {
                int length = Math.min(257, export.length - offset);
                ByteBuffer buffer = ByteBuffer.allocateDirect(length);
                buffer.put(export, offset, length).flip();
                assertThat(compressed.write(buffer)).isEqualTo(length);
            }
            compressed.finishBlock();
        }

        byte[] encoded = compressedBytes.toByteArray();
        assertThat(encoded).hasSizeLessThan(export.length);
        assertThat(readBack(encoded)).containsExactly(export);
    }

    private static byte[] readBack(byte[] encoded) throws IOException {
        ByteArrayOutputStream restored = new ByteArrayOutputStream();
        try (LZFInputStream compressed = new LZFInputStream(
                new ByteArrayInputStream(encoded), true)) {
            byte[] buffer = new byte[431];
            int read;
            while ((read = compressed.read(buffer)) != -1) {
                restored.write(buffer, 0, read);
            }
        }
        return restored.toByteArray();
    }

    private static byte[] applicationExport() {
        StringBuilder export = new StringBuilder();
        for (int row = 0; row < 12000; row++) {
            export.append("account=")
                    .append(row % 97)
                    .append(" warehouse=7 status=processed event=inventory-reconciled\n");
        }
        return export.toString().getBytes(StandardCharsets.UTF_8);
    }
}
