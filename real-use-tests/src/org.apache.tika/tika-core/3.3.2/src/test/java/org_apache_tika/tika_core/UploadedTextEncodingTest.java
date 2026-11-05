/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.detect.NonDetectingEncodingDetector;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Tika's character encoding detection subsystem for uploaded text.
public class UploadedTextEncodingTest {
    @Test
    @Timeout(60)
    public void readsUtf16TextWithTheUploadEncoding() throws Exception {
        String message = "Invoice ready: cafe\u00e9";
        byte[] encodedMessage = withUtf16LittleEndianBom(message);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "invoice.txt");
        metadata.set(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-16LE");

        try (ByteArrayInputStream upload = new ByteArrayInputStream(encodedMessage);
                AutoDetectReader reader = new AutoDetectReader(upload, metadata,
                        new NonDetectingEncodingDetector(StandardCharsets.UTF_16LE))) {
            assertEquals(StandardCharsets.UTF_16LE, reader.getCharset());
            assertEquals(message, readAll(reader));
        }
    }

    private static byte[] withUtf16LittleEndianBom(String value) {
        byte[] content = value.getBytes(StandardCharsets.UTF_16LE);
        byte[] encoded = new byte[content.length + 2];
        encoded[0] = (byte) 0xff;
        encoded[1] = (byte) 0xfe;
        System.arraycopy(content, 0, encoded, 2, content.length);
        return encoded;
    }

    private static String readAll(AutoDetectReader reader) throws IOException {
        StringBuilder content = new StringBuilder();
        char[] buffer = new char[32];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            content.append(buffer, 0, count);
        }
        return content.toString();
    }
}
