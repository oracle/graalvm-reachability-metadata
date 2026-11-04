/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ning.compress_lzf;

import static org.assertj.core.api.Assertions.assertThat;

import com.ning.compress.lzf.LZFCompressingInputStream;
import com.ning.compress.lzf.LZFInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The input-stream compression subsystem is exercised by preparing a compressed upload stream.
 */
public class InputStreamCompressionTest {
    @Test
    public void compressesAnApplicationUploadAsItIsRead() throws IOException {
        byte[] upload = applicationUpload();
        ByteArrayOutputStream compressedUpload = new ByteArrayOutputStream();

        try (LZFCompressingInputStream compressed = new LZFCompressingInputStream(
                new ByteArrayInputStream(upload))) {
            byte[] buffer = new byte[311];
            int read;
            while ((read = compressed.read(buffer)) != -1) {
                compressedUpload.write(buffer, 0, read);
            }
        }

        byte[] encoded = compressedUpload.toByteArray();
        assertThat(encoded).hasSizeLessThan(upload.length);

        ByteArrayOutputStream restoredUpload = new ByteArrayOutputStream();
        try (LZFInputStream restored = new LZFInputStream(
                new ByteArrayInputStream(encoded), true)) {
            byte[] buffer = new byte[257];
            int read;
            while ((read = restored.read(buffer)) != -1) {
                restoredUpload.write(buffer, 0, read);
            }
        }

        assertThat(restoredUpload.toByteArray()).containsExactly(upload);
    }

    private static byte[] applicationUpload() {
        StringBuilder upload = new StringBuilder();
        for (int event = 0; event < 8192; event++) {
            upload.append("event=")
                    .append(event)
                    .append(" tenant=warehouse-7 status=accepted source=mobile\\n");
        }
        return upload.toString().getBytes(StandardCharsets.UTF_8);
    }
}
