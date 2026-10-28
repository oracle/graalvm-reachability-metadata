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
import org.ehcache.spi.loaderwriter.CacheLoaderWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises Ehcache's read-through/write-through loader-writer subsystem for a file-backed product catalog.
 * A storefront reads product descriptions from its catalog, publishes a price change through the cache,
 * and retires a product without leaving a stale entry in either the cache or the catalog files.
 */
public class ProductCatalogLoaderWriterTest {
    @TempDir
    Path catalogDirectory;

    @Test
    @Timeout(60)
    public void updatesAndRetiresProductsThroughTheCatalogCache() throws IOException {
        Path coffeeFile = catalogDirectory.resolve("coffee.txt");
        Path teaFile = catalogDirectory.resolve("tea.txt");
        Files.writeString(coffeeFile, "Roasted coffee | 1299");
        Files.writeString(teaFile, "Green tea | 799");
        CatalogFiles catalog = new CatalogFiles(catalogDirectory);

        try (CacheManager manager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("products", CacheConfigurationBuilder.newCacheConfigurationBuilder(
                        String.class, String.class, ResourcePoolsBuilder.heap(100))
                        .withLoaderWriter(catalog))
                .build(true)) {
            Cache<String, String> products = manager.getCache("products", String.class, String.class);

            assertEquals("Roasted coffee | 1299", products.get("coffee"));
            assertEquals("Green tea | 799", products.get("tea"));
            assertNull(products.get("unknown"));

            products.put("coffee", "Roasted coffee | 1199");
            assertEquals("Roasted coffee | 1199", products.get("coffee"));
            assertEquals("Roasted coffee | 1199", Files.readString(coffeeFile));

            // Clearing the cache invalidates local copies without deleting the source catalog.
            products.clear();
            assertEquals("Roasted coffee | 1199", products.get("coffee"));
            assertEquals("Green tea | 799", products.get("tea"));

            products.remove("tea");
            assertFalse(Files.exists(teaFile));
            assertNull(products.get("tea"));

            products.clear();
            assertNull(products.get("tea"));
            assertEquals("Roasted coffee | 1199", products.get("coffee"));
        }
    }

    private static final class CatalogFiles implements CacheLoaderWriter<String, String> {
        private final Path directory;

        private CatalogFiles(Path directory) {
            this.directory = directory;
        }

        @Override
        public String load(String sku) throws IOException {
            Path productFile = directory.resolve(sku + ".txt");
            return Files.exists(productFile) ? Files.readString(productFile) : null;
        }

        @Override
        public void write(String sku, String description) throws IOException {
            Files.writeString(directory.resolve(sku + ".txt"), description);
        }

        @Override
        public void delete(String sku) throws IOException {
            Files.deleteIfExists(directory.resolve(sku + ".txt"));
        }
    }
}
