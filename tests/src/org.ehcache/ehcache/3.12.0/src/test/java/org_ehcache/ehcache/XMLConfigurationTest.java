/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ehcache.ehcache;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.Configuration;
import org.ehcache.config.ResourceType;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.core.config.DefaultConfiguration;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.ehcache.xml.XmlConfiguration;
import org.ehcache.xml.multi.XmlMultiConfiguration;
import org.junit.jupiter.api.Test;

import javax.cache.Caching;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class XMLConfigurationTest {
    @Test
    void testXMLProgrammaticParsingAndProgrammaticConfigurationToXML() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        URL resource = getClass().getResource("/template-sample.xml");
        assertThat(resource).isNotNull();
        CacheConfigurationBuilder<Long, String> configurationBuilder = new XmlConfiguration(resource)
                .newCacheConfigurationBuilderFromTemplate("example", Long.class, String.class)
                .withResourcePools(ResourcePoolsBuilder.heap(1000L));
        try (CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("myCache", configurationBuilder)
                .build(true)) {
            Cache<Long, String> myCache = cacheManager.getCache("myCache", Long.class, String.class);
            myCache.put(1L, "da one!");
            assertThat(myCache.get(1L)).isEqualTo("da one!");
            assertThat(myCache.getRuntimeConfiguration().getResourcePools().getPoolForResource(ResourceType.Core.HEAP).getSize()).isEqualTo(1000L);
            assertThat(new XmlConfiguration(cacheManager.getRuntimeConfiguration()).toString())
                    .startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>");
        }
    }

    @Test
    void testMultipleXMLConfigurationsInOneDocument() {
        URL resource = getClass().getResource("/multiple-managers.xml");
        assertThat(resource).isNotNull();
        assertThat(XmlMultiConfiguration.from(resource).build().configuration("foo-manager")).isNotNull();
    }

    @Test
    void testMultipleEhcacheManagerConfigurations() {
        URL resource = getClass().getResource("/multiple-variants.xml");
        assertThat(resource).isNotNull();
        assertThat(XmlMultiConfiguration.from(resource).build().configuration("foo-manager", "offHeap")).isNotNull();
    }

    @Test
    void testMultipleCacheManagerRetrieval() {
        URL multipleManagersResource = getClass().getResource("/multiple-managers.xml");
        URL multipleVariantsResource = getClass().getResource("/multiple-variants.xml");
        assertThat(multipleManagersResource).isNotNull();
        assertThat(multipleVariantsResource).isNotNull();
        XmlMultiConfiguration multipleConfiguration = XmlMultiConfiguration.from(multipleManagersResource).build();
        XmlMultiConfiguration variantConfiguration = XmlMultiConfiguration.from(multipleVariantsResource).build();
        assertThat(multipleConfiguration.identities().stream().collect(Collectors.toMap(i -> i, multipleConfiguration::configuration))).isNotNull();
        assertThat(variantConfiguration.identities().stream()
                .collect(Collectors.toMap(i -> i, i -> variantConfiguration.configuration(i, "offHeap")))).isNotNull();
    }

    @Test
    void testBuildingXMLMultiConfigurations() {
        Map<String, CacheConfiguration<?, ?>> caches = new HashMap<>();
        caches.put("preConfigured", CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class,
                ResourcePoolsBuilder.heap(10L)).build());
        EhcacheCachingProvider provider = (EhcacheCachingProvider) Caching.getCachingProvider(EhcacheCachingProvider.class.getName());
        Configuration barConfiguration = new DefaultConfiguration(caches, provider.getDefaultClassLoader());
        XmlMultiConfiguration multiConfiguration = XmlMultiConfiguration.fromNothing()
                .withManager("bar", barConfiguration)
                .withManager("foo").variant("heap", barConfiguration).variant("offHeap", barConfiguration)
                .build();
        assertThat(multiConfiguration).isNotNull();
        assertThat(XmlMultiConfiguration.from(multiConfiguration).withManager("foo").build()).isNotNull();
        assertThat(multiConfiguration.asRenderedDocument()).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>");
        assertThat(multiConfiguration.asDocument().toString()).isEqualTo("[#document: null]");
    }
}
