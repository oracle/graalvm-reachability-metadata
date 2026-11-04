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
import com.ning.compress.lzf.LZFException;
import com.ning.compress.lzf.util.ChunkDecoderFactory;
import com.ning.compress.lzf.util.ChunkEncoderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The selective chunk ingestion subsystem is exercised by filtering a streamed event batch.
 */
public class SelectiveChunkIngestionTest {
    @Test
    public void skipsSmallEventsAndDecodesLargeEvents() throws IOException, LZFException {
        byte[] smallEvent = "event=heartbeat tenant=warehouse-7".getBytes(StandardCharsets.UTF_8);
        byte[] largeEvent = largeEvent();
        byte[] encodedEvents = encodeEvents(smallEvent, largeEvent);
        ChunkDecoder decoder = ChunkDecoderFactory.safeInstance();
        byte[] headerBuffer = new byte[largeEvent.length];
        byte[] decodedBuffer = new byte[largeEvent.length];

        try (ByteArrayInputStream input = new ByteArrayInputStream(encodedEvents)) {
            int skipped = decoder.skipOrDecodeChunk(
                    input, headerBuffer, decodedBuffer, largeEvent.length / 2L);
            int decoded = decoder.skipOrDecodeChunk(
                    input, headerBuffer, decodedBuffer, largeEvent.length / 2L);

            assertThat(skipped).isEqualTo(smallEvent.length);
            assertThat(decoded).isEqualTo(-(largeEvent.length + 1));
            assertThat(decodedBuffer).containsExactly(largeEvent);
            assertThat(input.read()).isEqualTo(-1);
        }
    }

    private static byte[] encodeEvents(byte[] smallEvent, byte[] largeEvent) throws IOException {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (ChunkEncoder encoder = ChunkEncoderFactory.safeInstance()) {
            writeChunk(encoded, encoder.encodeChunk(smallEvent, 0, smallEvent.length));
            writeChunk(encoded, encoder.encodeChunk(largeEvent, 0, largeEvent.length));
        }
        return encoded.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream output, LZFChunk chunk) {
        byte[] encodedChunk = new byte[chunk.length()];
        assertThat(chunk.copyTo(encodedChunk, 0)).isEqualTo(chunk.length());
        output.writeBytes(encodedChunk);
    }

    private static byte[] largeEvent() {
        StringBuilder event = new StringBuilder();
        for (int attribute = 0; attribute < 512; attribute++) {
            event.append("attribute=")
                    .append(attribute)
                    .append(" tenant=warehouse-7 type=inventory status=accepted\n");
        }
        return event.toString().getBytes(StandardCharsets.UTF_8);
    }
}
