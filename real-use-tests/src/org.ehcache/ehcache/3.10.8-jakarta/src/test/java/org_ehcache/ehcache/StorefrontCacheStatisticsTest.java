/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ehcache.ehcache;

import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.jsr107.Eh107Configuration;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.Configuration;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Ehcache's JCache statistics subsystem for a storefront cache-health dashboard.
 * Operators measure page-cache effectiveness and start a fresh reporting window without discarding warm pages.
 */
public class StorefrontCacheStatisticsTest {
    @Test
    @Timeout(60)
    public void reportsPageCacheHitsAndMissesAcrossReportingWindows() throws Exception {
        MBeanServer diagnostics = ManagementFactory.getPlatformMBeanServer();
        try (EhcacheCachingProvider provider = new EhcacheCachingProvider();
                CacheManager manager = provider.getCacheManager()) {
            Configuration<String, String> configuration = Eh107Configuration.fromEhcacheCacheConfiguration(
                    CacheConfigurationBuilder.newCacheConfigurationBuilder(
                            String.class, String.class, ResourcePoolsBuilder.heap(100)));
            Cache<String, String> pages = manager.createCache("storefront-page-metrics", configuration);
            manager.enableStatistics("storefront-page-metrics", true);
            Set<ObjectName> statisticsBeans = diagnostics.queryNames(new ObjectName(
                    "javax.cache:type=CacheStatistics,Cache=storefront-page-metrics,*"), null);
            assertEquals(1, statisticsBeans.size(), "The dashboard must discover the page cache's statistics");
            ObjectName statistics = statisticsBeans.iterator().next();
            PageRenderer renderer = new PageRenderer(pages, Map.of(
                    "coffee", "Seasonal coffee | 1299",
                    "tea", "Green tea | 799"));

            assertEquals("<article>Seasonal coffee | 1299</article>", renderer.render("coffee"));
            assertEquals("<article>Seasonal coffee | 1299</article>", renderer.render("coffee"));
            assertEquals("<article>Green tea | 799</article>", renderer.render("tea"));
            assertEquals("<article>Seasonal coffee | 1299</article>", renderer.render("coffee"));

            assertEquals(4L, diagnostics.getAttribute(statistics, "CacheGets"));
            assertEquals(2L, diagnostics.getAttribute(statistics, "CacheHits"));
            assertEquals(2L, diagnostics.getAttribute(statistics, "CacheMisses"));
            assertEquals(2L, diagnostics.getAttribute(statistics, "CachePuts"));
            assertEquals(50.0f, diagnostics.getAttribute(statistics, "CacheHitPercentage"));

            // Reset only the reporting window, not the pages serving active shoppers.
            diagnostics.invoke(statistics, "clear", new Object[0], new String[0]);
            assertEquals(0L, diagnostics.getAttribute(statistics, "CacheGets"));
            assertEquals(0L, diagnostics.getAttribute(statistics, "CachePuts"));
            assertEquals("<article>Seasonal coffee | 1299</article>", renderer.render("coffee"));
            assertEquals("<article>Green tea | 799</article>", renderer.render("tea"));

            assertEquals(2L, diagnostics.getAttribute(statistics, "CacheGets"));
            assertEquals(2L, diagnostics.getAttribute(statistics, "CacheHits"));
            assertEquals(0L, diagnostics.getAttribute(statistics, "CacheMisses"));
            assertEquals(0L, diagnostics.getAttribute(statistics, "CachePuts"));
            assertEquals(100.0f, diagnostics.getAttribute(statistics, "CacheHitPercentage"));
        }
    }

    private static final class PageRenderer {
        private final Cache<String, String> pages;
        private final Map<String, String> catalog;

        private PageRenderer(Cache<String, String> pages, Map<String, String> catalog) {
            this.pages = pages;
            this.catalog = catalog;
        }

        private String render(String product) {
            String page = pages.get(product);
            if (page == null) {
                page = "<article>" + catalog.get(product) + "</article>";
                pages.put(product, page);
            }
            return page;
        }
    }
}
