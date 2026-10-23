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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises Ehcache's cache iteration subsystem for selective storefront page invalidation.
 * A catalog publication invalidates every cached view of one category while retaining unrelated pages.
 */
public class CategoryPageInvalidationTest {
    @Test
    @Timeout(60)
    public void invalidatesAllLocalizedViewsOfThePublishedCategory() {
        try (CacheManager manager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("category-pages", CacheConfigurationBuilder.newCacheConfigurationBuilder(
                        String.class, CategoryPage.class, ResourcePoolsBuilder.heap(100)))
                .build(true)) {
            Cache<String, CategoryPage> pages = manager.getCache(
                    "category-pages", String.class, CategoryPage.class);
            CategoryPage englishCoffee = new CategoryPage("coffee", "Coffee: classic roast");
            CategoryPage frenchCoffee = new CategoryPage("coffee", "Cafe: torrefaction classique");
            CategoryPage englishTea = new CategoryPage("tea", "Tea: green selection");
            CategoryPage frenchTea = new CategoryPage("tea", "The: selection verte");
            CategoryPage cocoa = new CategoryPage("cocoa", "Cocoa: sampler");
            pages.putAll(Map.of(
                    "/en/coffee", englishCoffee,
                    "/fr/coffee", frenchCoffee,
                    "/en/tea", englishTea,
                    "/fr/tea", frenchTea,
                    "/en/cocoa", cocoa));

            // Discover all localized views from their category tags, not from a hardcoded URL list.
            assertEquals(Set.of("/en/coffee", "/fr/coffee"), invalidateCategory(pages, "coffee"));
            assertNull(pages.get("/en/coffee"));
            assertNull(pages.get("/fr/coffee"));
            assertEquals(Map.of(
                    "/en/tea", englishTea,
                    "/fr/tea", frenchTea,
                    "/en/cocoa", cocoa), snapshot(pages));

            // The next request renders the new publication, without resurrecting other stale views.
            CategoryPage seasonalCoffee = new CategoryPage("coffee", "Coffee: seasonal roast");
            pages.put("/en/coffee", seasonalCoffee);
            assertEquals(seasonalCoffee, pages.get("/en/coffee"));
            assertNull(pages.get("/fr/coffee"));

            assertEquals(Set.of(), invalidateCategory(pages, "biscuits"));
            assertEquals(Set.of("/en/tea", "/fr/tea"), invalidateCategory(pages, "tea"));
            assertEquals(Map.of("/en/coffee", seasonalCoffee, "/en/cocoa", cocoa), snapshot(pages));
        }
    }

    private static Set<String> invalidateCategory(Cache<String, CategoryPage> pages, String category) {
        Set<String> staleUrls = new HashSet<>();
        for (Cache.Entry<String, CategoryPage> entry : pages) {
            if (category.equals(entry.getValue().category())) {
                staleUrls.add(entry.getKey());
            }
        }
        // Finish scanning before removing entries; iteration does not promise snapshot isolation.
        pages.removeAll(staleUrls);
        return staleUrls;
    }

    private static Map<String, CategoryPage> snapshot(Cache<String, CategoryPage> pages) {
        Map<String, CategoryPage> result = new HashMap<>();
        for (Cache.Entry<String, CategoryPage> entry : pages) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private record CategoryPage(String category, String body) {
    }
}
