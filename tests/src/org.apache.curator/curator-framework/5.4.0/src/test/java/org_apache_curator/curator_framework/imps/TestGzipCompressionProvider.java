/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework.imps;

import org.apache.curator.framework.imps.GzipCompressionProvider;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("unused")
public class TestGzipCompressionProvider {
    @Test
    public void testDecompressCorrupt() {
        GzipCompressionProvider provider = new GzipCompressionProvider();
        try {
            provider.decompress(null, new byte[100]);
            fail("Expected IOException");
        } catch (IOException ignore) {
        }
        byte[] compressedData = provider.compress(null, new byte[0]);
        for (int i = 0; i < compressedData.length; i++) {
            try {
                provider.decompress(null, Arrays.copyOf(compressedData, i));
            } catch (IOException ignore) {
            }
            for (int change = 1; change < 256; change++) {
                byte b = compressedData[i];
                compressedData[i] = (byte) (b + change);
                try {
                    provider.decompress(null, compressedData);
                } catch (IOException ignore) {
                }
                compressedData[i] = b;
            }
        }
    }

    private static byte[] jdkCompress(byte[] data) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
            out.write(data);
            out.finish();
        }
        return bytes.toByteArray();
    }
}
