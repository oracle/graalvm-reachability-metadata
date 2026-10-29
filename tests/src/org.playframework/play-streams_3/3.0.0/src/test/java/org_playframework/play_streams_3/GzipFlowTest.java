/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_streams_3;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;

import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import play.api.libs.streams.GzipFlow;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class GzipFlowTest extends StreamTestSupport {
    @Test
    void compressesAcrossInputAndBufferBoundaries() throws Exception {
        List<ByteString> chunks = List.of(ByteString.fromString("first "), ByteString.emptyByteString(),
                ByteString.fromString("second "), ByteString.fromString("third"));

        assertEquals("first second third", decompress(compress(chunks, 4, Deflater.DEFAULT_COMPRESSION)));
    }

    @Test
    void compressesLargeUtf8PayloadWithExplicitCompressionLevel() throws Exception {
        String text = "streams caf\u00e9 \u2603 ".repeat(200);

        assertEquals(text, decompress(compress(List.of(ByteString.fromString(text)), 32, Deflater.BEST_COMPRESSION)));
    }

    @Test
    void producesValidGzipForAnEmptyStream() throws Exception {
        assertEquals("", decompress(compress(List.of(), 16, Deflater.DEFAULT_COMPRESSION)));
    }

    private ByteString compress(List<ByteString> chunks, int bufferSize, int compressionLevel) throws Exception {
        return await(Source.from(chunks).via(GzipFlow.gzip(bufferSize, compressionLevel).asJava())
                .runFold(ByteString.emptyByteString(), ByteString::concat, materializer));
    }

    private static String decompress(ByteString compressed) throws Exception {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed.toArray()))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
