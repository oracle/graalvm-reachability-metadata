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
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The conditional chunk compression subsystem is exercised by preparing responses for an API.
 */
public class ConditionalChunkCompressionTest {
    @Test
    public void compressesOnlyResponsesThatBenefitFromLzf() throws Exception {
        byte[] repetitiveResponse = repetitiveResponse();
        byte[] binaryResponse = binaryResponse();
        ChunkDecoder decoder = ChunkDecoderFactory.safeInstance();

        try (ChunkEncoder encoder = ChunkEncoderFactory.safeInstance()) {
            LZFChunk compressed = encoder.encodeChunkIfCompresses(
                    repetitiveResponse, 0, repetitiveResponse.length, 0.9);
            LZFChunk uncompressed = encoder.encodeChunkIfCompresses(
                    binaryResponse, 0, binaryResponse.length, 0.9);

            assertThat(compressed).isNotNull();
            assertThat(uncompressed).isNull();
            assertThat(decode(decoder, compressed)).containsExactly(repetitiveResponse);

            LZFChunk fallback = LZFChunk.createNonCompressed(
                    binaryResponse, 0, binaryResponse.length);
            assertThat(decode(decoder, fallback)).containsExactly(binaryResponse);
        }
    }

    private static byte[] decode(ChunkDecoder decoder, LZFChunk chunk) throws Exception {
        byte[] encoded = new byte[chunk.length()];
        chunk.copyTo(encoded, 0);
        return decoder.decode(encoded);
    }

    private static byte[] repetitiveResponse() {
        StringBuilder response = new StringBuilder();
        for (int item = 0; item < 1024; item++) {
            response.append("item=")
                    .append(item)
                    .append(" warehouse=7 state=ready operation=replenish\n");
        }
        return response.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] binaryResponse() {
        byte[] response = new byte[32768];
        int state = 0x13579bdf;
        for (int index = 0; index < response.length; index++) {
            state = state * 1664525 + 1013904223;
            response[index] = (byte) (state >>> 24);
        }
        return response;
    }
}
