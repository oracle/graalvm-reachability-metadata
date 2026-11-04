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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The chunk-chain assembly subsystem is exercised by framing a large application batch for transport.
 */
public class ChunkChainAssemblyTest {
    @Test
    public void assemblesAndRestoresAChunkedApplicationBatch() throws IOException {
        byte[] batch = applicationBatch();
        LZFChunk firstChunk = null;
        LZFChunk lastChunk = null;
        int chunkCount = 0;

        try (ChunkEncoder encoder = ChunkEncoderFactory.safeInstance(16 * 1024)) {
            for (int offset = 0; offset < batch.length; offset += 16 * 1024) {
                int length = Math.min(16 * 1024, batch.length - offset);
                LZFChunk chunk = encoder.encodeChunk(batch, offset, length);
                if (firstChunk == null) {
                    firstChunk = chunk;
                } else {
                    lastChunk.setNext(chunk);
                }
                lastChunk = chunk;
                chunkCount++;
            }
        }

        ByteArrayOutputStream encodedBatch = new ByteArrayOutputStream();
        int linkedChunkCount = 0;
        for (LZFChunk chunk = firstChunk; chunk != null; chunk = chunk.next()) {
            byte[] encodedChunk = new byte[chunk.length()];
            assertThat(chunk.copyTo(encodedChunk, 0)).isEqualTo(chunk.length());
            encodedBatch.write(encodedChunk);
            linkedChunkCount++;
        }

        byte[] encoded = encodedBatch.toByteArray();
        ChunkDecoder decoder = ChunkDecoderFactory.safeInstance();

        assertThat(chunkCount).isGreaterThan(1);
        assertThat(linkedChunkCount).isEqualTo(chunkCount);
        assertThat(encoded).hasSizeLessThan(batch.length);
        assertThat(decoder.decode(encoded)).containsExactly(batch);
    }

    private static byte[] applicationBatch() {
        StringBuilder batch = new StringBuilder();
        for (int record = 0; record < 12000; record++) {
            batch.append("record=")
                    .append(record)
                    .append(" warehouse=7 event=inventory-reconciled status=complete\n");
        }
        return batch.toString().getBytes(StandardCharsets.UTF_8);
    }
}
