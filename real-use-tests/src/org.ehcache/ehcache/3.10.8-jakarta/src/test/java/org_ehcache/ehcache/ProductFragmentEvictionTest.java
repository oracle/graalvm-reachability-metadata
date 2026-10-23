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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Ehcache's heap-capacity eviction subsystem for rendered storefront product fragments.
 * A bounded cache serves a larger catalog by rendering evicted fragments again from their source files.
 */
public class ProductFragmentEvictionTest {
    @TempDir
    Path catalogDirectory;

    @Test
    @Timeout(60)
    public void servesTheWholeCatalogDespiteEvictionFromTheBoundedFragmentCache() throws IOException {
        for (int product = 0; product < 40; product++) {
            Files.writeString(catalogDirectory.resolve("product-" + product + ".txt"), "Product " + product);
        }

        try (CacheManager manager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("product-fragments", CacheConfigurationBuilder.newCacheConfigurationBuilder(
                        String.class, String.class, ResourcePoolsBuilder.heap(10)))
                .build(true)) {
            Cache<String, String> fragments = manager.getCache("product-fragments", String.class, String.class);
            FragmentRenderer renderer = new FragmentRenderer(fragments, catalogDirectory);
            for (int product = 0; product < 40; product++) {
                assertEquals("<article>Product " + product + "</article>", renderer.render("product-" + product));
            }
            assertEquals(40, renderer.sourceReads);

            Map<String, String> retained = snapshot(fragments);
            assertFalse(retained.isEmpty());
            assertTrue(retained.size() <= 10, "The catalog must not exceed the cache's entry budget");

            // Reusing a retained fragment does not render it from disk again.
            String retainedProduct = retained.keySet().iterator().next();
            assertEquals(retained.get(retainedProduct), renderer.render(retainedProduct));
            assertEquals(40, renderer.sourceReads);

            // Do not depend on which entries were evicted or on the cache's sampling order.
            for (int product = 0; product < 40; product++) {
                assertEquals("<article>Product " + product + "</article>", renderer.render("product-" + product));
            }
            assertTrue(renderer.sourceReads >= 70, "At least thirty evicted fragments must be rendered again");
            assertTrue(snapshot(fragments).size() <= 10, "Reloading must preserve the entry budget");
        }
    }

    private static Map<String, String> snapshot(Cache<String, String> fragments) {
        Map<String, String> result = new HashMap<>();
        for (Cache.Entry<String, String> fragment : fragments) {
            result.put(fragment.getKey(), fragment.getValue());
        }
        return result;
    }

    private static final class FragmentRenderer {
        private final Cache<String, String> fragments;
        private final Path directory;
        private int sourceReads;

        private FragmentRenderer(Cache<String, String> fragments, Path directory) {
            this.fragments = fragments;
            this.directory = directory;
        }

        private String render(String product) throws IOException {
            String fragment = fragments.get(product);
            if (fragment == null) {
                String description = Files.readString(directory.resolve(product + ".txt"));
                sourceReads++;
                fragment = "<article>" + description + "</article>";
                fragments.put(product, fragment);
            }
            return fragment;
        }
    }
}
