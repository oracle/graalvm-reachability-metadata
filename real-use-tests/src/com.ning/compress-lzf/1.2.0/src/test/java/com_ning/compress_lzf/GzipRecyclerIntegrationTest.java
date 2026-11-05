/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ning.compress_lzf;

import static org.assertj.core.api.Assertions.assertThat;

import com.ning.compress.BufferRecycler;
import com.ning.compress.gzip.GZIPRecycler;
import com.ning.compress.gzip.OptimizedGZIPInputStream;
import com.ning.compress.gzip.OptimizedGZIPOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.junit.jupiter.api.Test;

/**
 * The gzip recycler subsystem is exercised by restoring a compressed application response.
 */
public class GzipRecyclerIntegrationTest {
    @Test
    public void restoresAResponseWithApplicationManagedGzipResources() throws IOException {
        byte[] response = applicationResponse();
        byte[] compressed = compress(response);
        GZIPRecycler recycler = new GZIPRecycler();

        ByteArrayOutputStream restored = new ByteArrayOutputStream();
        try (OptimizedGZIPInputStream input = new OptimizedGZIPInputStream(
                new ByteArrayInputStream(compressed), new BufferRecycler(), recycler)) {
            byte[] buffer = new byte[173];
            int read;
            while ((read = input.read(buffer)) != -1) {
                restored.write(buffer, 0, read);
            }
        }

        assertThat(restored.toByteArray()).containsExactly(response);
        Inflater inflater = recycler.allocInflater();
        Deflater deflater = recycler.allocDeflater();
        assertThat(inflater).isNotNull();
        assertThat(deflater).isNotNull();
        recycler.releaseInflater(inflater);
        recycler.releaseDeflater(deflater);
    }

    private static byte[] compress(byte[] response) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (OptimizedGZIPOutputStream output = new OptimizedGZIPOutputStream(compressed)) {
            output.write(response);
        }
        return compressed.toByteArray();
    }

    private static byte[] applicationResponse() {
        StringBuilder response = new StringBuilder();
        for (int item = 0; item < 4096; item++) {
            response.append("item=")
                    .append(item)
                    .append(" tenant=warehouse-7 status=ready operation=replenish\n");
        }
        return response.toString().getBytes(StandardCharsets.UTF_8);
    }
}
