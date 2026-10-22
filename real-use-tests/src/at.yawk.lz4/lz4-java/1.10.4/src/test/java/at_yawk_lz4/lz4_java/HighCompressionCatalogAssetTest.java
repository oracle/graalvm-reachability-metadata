/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises LZ4 high-compression encoding for a prebuilt offline product catalog asset.
/// A release job compresses the catalog once, and a client restores it with the standard LZ4 decoder.
public class HighCompressionCatalogAssetTest {
    @TempDir
    Path directory;

    @Test
    @Timeout(60)
    public void publishesCompactCatalogThatAnOfflineClientCanSearch() throws IOException {
        List<Product> published = new ArrayList<>();
        for (int item = 0; item < 600; item++) {
            published.add(new Product("SKU-" + item, "Storage container " + item,
                    item % 2 == 0 ? "warehouse" : "office", 1200L + item,
                    "Stackable storage container with reinforced handles and a reusable lid. "
                            + "Available for collection from the regional distribution center."));
        }

        byte[] source = encode(published);
        LZ4Factory factory = LZ4Factory.fastestInstance();
        LZ4Compressor releaseCompressor = factory.highCompressor();
        byte[] packed = releaseCompressor.compress(source);
        Path asset = directory.resolve("offline-catalog.bin.lz4");
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(asset))) {
            output.writeUTF("offline-catalog-v1");
            // Raw LZ4 blocks need the original size supplied by the asset envelope.
            output.writeInt(source.length);
            output.writeInt(packed.length);
            output.write(packed);
        }
        assertTrue(Files.size(asset) < source.length,
                "The published asset should reduce the bytes an offline client must download");

        byte[] restored;
        try (DataInputStream input = new DataInputStream(Files.newInputStream(asset))) {
            assertEquals("offline-catalog-v1", input.readUTF());
            int originalLength = input.readInt();
            int compressedLength = input.readInt();
            byte[] downloaded = new byte[compressedLength];
            input.readFully(downloaded);
            assertEquals(-1, input.read(), "The asset contains exactly one catalog");
            restored = factory.safeDecompressor().decompress(downloaded, originalLength);
            assertEquals(originalLength, restored.length, "The entire catalog must be restored");
        }

        List<Product> offlineCatalog = decode(restored);
        assertEquals(published, offlineCatalog, "Every product field must survive release packaging");
        List<String> affordableWarehouseItems = offlineCatalog.stream()
                .filter(product -> product.category().equals("warehouse") && product.priceCents() < 1210L)
                .map(Product::sku)
                .toList();
        assertEquals(List.of("SKU-0", "SKU-2", "SKU-4", "SKU-6", "SKU-8"),
                affordableWarehouseItems, "Offline search must retain category and price information");
    }

    private static byte[] encode(List<Product> products) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(products.size());
            for (Product product : products) {
                output.writeUTF(product.sku());
                output.writeUTF(product.name());
                output.writeUTF(product.category());
                output.writeLong(product.priceCents());
                output.writeUTF(product.description());
            }
        }
        return bytes.toByteArray();
    }

    private static List<Product> decode(byte[] bytes) throws IOException {
        List<Product> products = new ArrayList<>();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int count = input.readInt();
            for (int item = 0; item < count; item++) {
                products.add(new Product(input.readUTF(), input.readUTF(), input.readUTF(),
                        input.readLong(), input.readUTF()));
            }
            assertEquals(-1, input.read(), "Catalog parsing must consume the complete decoded asset");
        }
        return products;
    }

    private record Product(String sku, String name, String category, long priceCents, String description) {
    }
}
