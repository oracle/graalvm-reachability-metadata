/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ning.compress_lzf;

import static org.assertj.core.api.Assertions.assertThat;

import com.ning.compress.DataHandler;
import com.ning.compress.UncompressorOutputStream;
import com.ning.compress.gzip.GZIPUncompressor;
import com.ning.compress.gzip.OptimizedGZIPOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The gzip callback decompression subsystem is exercised by consuming a streamed event upload.
 */
public class GzipCallbackDecompressionTest {
    @Test
    public void consumesAnEventUploadDeliveredInNetworkChunks() throws IOException {
        byte[] upload = eventUpload();
        byte[] compressedUpload = gzip(upload);
        RecordingHandler handler = new RecordingHandler();

        try (UncompressorOutputStream input = new UncompressorOutputStream(
                new GZIPUncompressor(handler))) {
            for (int offset = 0; offset < compressedUpload.length; offset++) {
                input.write(compressedUpload, offset, 1);
            }
        }

        assertThat(handler.completed).isTrue();
        assertThat(handler.data()).containsExactly(upload);
    }

    private static byte[] gzip(byte[] upload) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (OptimizedGZIPOutputStream output = new OptimizedGZIPOutputStream(compressed)) {
            output.write(upload);
        }
        return compressed.toByteArray();
    }

    private static byte[] eventUpload() {
        StringBuilder upload = new StringBuilder();
        for (int event = 0; event < 4096; event++) {
            upload.append("event=")
                    .append(event)
                    .append(" tenant=warehouse-7 status=accepted source=mobile\n");
        }
        return upload.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static final class RecordingHandler implements DataHandler {
        private final ByteArrayOutputStream data = new ByteArrayOutputStream();
        private boolean completed;

        @Override
        public boolean handleData(byte[] buffer, int offset, int length) {
            data.write(buffer, offset, length);
            return true;
        }

        @Override
        public void allDataHandled() {
            completed = true;
        }

        private byte[] data() {
            return data.toByteArray();
        }
    }
}
