/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4SafeDecompressor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises raw LZ4 block compression for independently addressable pages in a sales report store.
/// An application index locates each compressed month without decompressing the preceding months.
public class IndexedSalesPageStoreTest {
    @TempDir
    Path directory;

    @Test
    @Timeout(60)
    public void retrievesCompressedMonthlyPagesInQueryOrder() throws IOException {
        LZ4Factory factory = LZ4Factory.fastestInstance();
        LZ4Compressor compressor = factory.fastCompressor();
        LZ4SafeDecompressor decompressor = factory.safeDecompressor();
        Path store = directory.resolve("sales-pages.bin");
        Map<String, PageLocation> index = new LinkedHashMap<>();
        long sourceBytes = 0;

        try (RandomAccessFile output = new RandomAccessFile(store.toFile(), "rw")) {
            for (String month : List.of("2025-01", "2025-02", "2025-03")) {
                byte[] page = salesPage(month);
                byte[] compressed = compressor.compress(page);
                index.put(month, new PageLocation(output.getFilePointer(), compressed.length, page.length));
                output.write(compressed);
                sourceBytes += page.length;
            }
        }

        assertTrue(Files.size(store) < sourceBytes,
                "Compressing repetitive sales pages should reduce the store's disk usage");

        try (RandomAccessFile input = new RandomAccessFile(store.toFile(), "r")) {
            for (String month : List.of("2025-03", "2025-01", "2025-02", "2025-03")) {
                PageLocation location = index.get(month);
                input.seek(location.offset());
                byte[] compressed = new byte[location.compressedLength()];
                input.readFully(compressed);
                byte[] restored = decompressor.decompress(compressed, location.originalLength());
                assertArrayEquals(salesPage(month), restored,
                        "A query for " + month + " must restore that month's complete sales page");
            }
        }
    }

    private static byte[] salesPage(String month) {
        StringBuilder page = new StringBuilder("month,order_id,region,product,quantity,total_cents\n");
        for (int order = 0; order < 2000; order++) {
            page.append(month).append(',').append(order)
                    .append(",north,warehouse-storage-box,2,4800\n");
        }
        return page.toString().getBytes(UTF_8);
    }

    private record PageLocation(long offset, int compressedLength, int originalLength) {
    }
}
