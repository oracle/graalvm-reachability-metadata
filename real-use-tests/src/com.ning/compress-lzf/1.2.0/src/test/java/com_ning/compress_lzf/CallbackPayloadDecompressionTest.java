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
import com.ning.compress.lzf.LZFDecoder;
import com.ning.compress.lzf.LZFEncoder;
import com.ning.compress.lzf.LZFException;
import com.ning.compress.lzf.LZFUncompressor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The callback decompression subsystem is exercised by consuming a compressed event upload.
 */
public class CallbackPayloadDecompressionTest {
    @Test
    public void consumesAnEventUploadDeliveredInNetworkChunks() throws IOException, LZFException {
        byte[] upload = eventUpload();
        byte[] compressedUpload = LZFEncoder.safeEncode(upload, 0, upload.length);
        RecordingHandler handler = new RecordingHandler();

        try (UncompressorOutputStream input = new UncompressorOutputStream(
                new LZFUncompressor(handler))) {
            for (int offset = 0; offset < compressedUpload.length; offset++) {
                input.write(compressedUpload, offset, 1);
            }
        }

        assertThat(handler.completed).isTrue();
        assertThat(handler.data()).containsExactly(upload);
        assertThat(LZFDecoder.safeDecode(compressedUpload)).containsExactly(upload);
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
