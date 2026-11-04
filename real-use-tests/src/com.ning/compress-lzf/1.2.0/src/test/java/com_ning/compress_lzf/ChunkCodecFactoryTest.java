/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ning.compress_lzf;

import static org.assertj.core.api.Assertions.assertThat;

import com.ning.compress.lzf.ChunkDecoder;
import com.ning.compress.lzf.ChunkEncoder;
import com.ning.compress.lzf.LZFChunk;
import com.ning.compress.lzf.util.ChunkDecoderFactory;
import com.ning.compress.lzf.util.ChunkEncoderFactory;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The chunk codec factory subsystem is exercised by compressing and restoring an application batch.
 */
public class ChunkCodecFactoryTest {
    @Test
    public void compressesAndRestoresChunkedApplicationBatch() throws Exception {
        byte[] batch = applicationBatch();
        ByteArrayOutputStream encodedBatch = new ByteArrayOutputStream();

        try (ChunkEncoder encoder = ChunkEncoderFactory.optimalInstance(16 * 1024)) {
            for (int offset = 0; offset < batch.length; offset += 16 * 1024) {
                int length = Math.min(16 * 1024, batch.length - offset);
                LZFChunk chunk = encoder.encodeChunk(batch, offset, length);
                byte[] encodedChunk = new byte[chunk.length()];
                chunk.copyTo(encodedChunk, 0);
                encodedBatch.write(encodedChunk);
            }
        }

        byte[] encoded = encodedBatch.toByteArray();
        ChunkDecoder decoder = ChunkDecoderFactory.optimalInstance();

        assertThat(encoded).hasSizeLessThan(batch.length);
        assertThat(decoder.decode(encoded)).containsExactly(batch);
    }

    private static byte[] applicationBatch() {
        StringBuilder batch = new StringBuilder();
        for (int record = 0; record < 4096; record++) {
            batch.append("record=")
                    .append(record)
                    .append(" tenant=warehouse-7 event=inventory-reconciled status=complete\n");
        }
        return batch.toString().getBytes(StandardCharsets.UTF_8);
    }
}
