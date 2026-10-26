/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ehcache.ehcache;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.builders.WriteBehindConfigurationBuilder;
import org.ehcache.spi.loaderwriter.CacheLoaderWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Ehcache's asynchronous write-behind batching subsystem for catalog publication.
 * A storefront queues a product feed and subsequent edits for export to catalog files in the background.
 */
public class CatalogWriteBehindTest {
    @TempDir
    Path catalogDirectory;

    @Test
    @Timeout(60)
    public void exportsPublishedProductsAndLaterEditsThroughBackgroundBatches() throws Exception {
        CatalogExporter exporter = new CatalogExporter(catalogDirectory);
        try (CacheManager manager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("published-catalog", CacheConfigurationBuilder.newCacheConfigurationBuilder(
                        String.class, String.class, ResourcePoolsBuilder.heap(100))
                        .withLoaderWriter(exporter)
                        .withService(WriteBehindConfigurationBuilder
                                .newBatchedWriteBehindConfiguration(1, TimeUnit.SECONDS, 2)
                                .concurrencyLevel(1)
                                .queueSize(16)))
                .build(true)) {
            Cache<String, String> products = manager.getCache("published-catalog", String.class, String.class);
            Map<String, String> publication = Map.of(
                    "coffee", "Roasted coffee | 1299",
                    "tea", "Green tea | 799",
                    "cocoa", "Cocoa sampler | 599");
            CountDownLatch publicationExported = exporter.expectChanges(publication.size());
            products.putAll(publication);
            assertEquals(publication, products.getAll(publication.keySet()));

            // The odd-sized feed also requires the final, partially filled batch to be delivered.
            assertTrue(publicationExported.await(20, TimeUnit.SECONDS), "Catalog publication was not exported");
            for (Map.Entry<String, String> product : publication.entrySet()) {
                assertEquals(product.getValue(), Files.readString(catalogDirectory.resolve(product.getKey() + ".txt")));
            }

            CountDownLatch editsExported = exporter.expectChanges(2);
            products.put("coffee", "Roasted coffee | 1199");
            products.remove("tea");
            assertEquals("Roasted coffee | 1199", products.get("coffee"));
            assertEquals("Cocoa sampler | 599", products.get("cocoa"));

            assertTrue(editsExported.await(20, TimeUnit.SECONDS), "Catalog edits were not exported");
            assertEquals("Roasted coffee | 1199", Files.readString(catalogDirectory.resolve("coffee.txt")));
            assertFalse(Files.exists(catalogDirectory.resolve("tea.txt")));
            assertEquals("Cocoa sampler | 599", Files.readString(catalogDirectory.resolve("cocoa.txt")));

            // A cold read must agree with the exported catalog, not just the heap's earlier values.
            products.clear();
            assertEquals("Roasted coffee | 1199", products.get("coffee"));
            assertNull(products.get("tea"));
            assertEquals("Cocoa sampler | 599", products.get("cocoa"));
        }
    }

    private static final class CatalogExporter implements CacheLoaderWriter<String, String> {
        private final Path directory;
        private volatile CountDownLatch pendingChanges = new CountDownLatch(0);

        private CatalogExporter(Path directory) {
            this.directory = directory;
        }

        private CountDownLatch expectChanges(int count) {
            pendingChanges = new CountDownLatch(count);
            return pendingChanges;
        }

        @Override
        public String load(String sku) throws IOException {
            Path productFile = directory.resolve(sku + ".txt");
            return Files.exists(productFile) ? Files.readString(productFile) : null;
        }

        @Override
        public void write(String sku, String description) throws IOException {
            Files.writeString(directory.resolve(sku + ".txt"), description);
            pendingChanges.countDown();
        }

        @Override
        public void delete(String sku) throws IOException {
            Files.deleteIfExists(directory.resolve(sku + ".txt"));
            pendingChanges.countDown();
        }
    }
}
