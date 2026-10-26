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
import org.ehcache.xml.XmlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises Ehcache's XML configuration and cache-template subsystem for tenant-specific product catalogs.
 * A storefront loads an existing tenant from XML and provisions new tenants from the same operations-owned template.
 */
public class TenantXmlTemplateTest {
    @TempDir
    Path configurationDirectory;

    @Test
    @Timeout(60)
    public void provisionsAndRecreatesTenantCachesWithoutSharingCatalogData() throws Exception {
        Path configurationFile = configurationDirectory.resolve("storefront.xml");
        Files.writeString(configurationFile, """
                <?xml version="1.0" encoding="UTF-8"?>
                <config xmlns="http://www.ehcache.org/v3">
                    <cache-template name="tenant-catalog">
                        <key-type>java.lang.String</key-type>
                        <value-type>java.lang.String</value-type>
                        <resources>
                            <heap unit="entries">100</heap>
                        </resources>
                    </cache-template>
                    <cache alias="north-catalog" uses-template="tenant-catalog"/>
                </config>
                """);
        XmlConfiguration configuration = new XmlConfiguration(configurationFile.toUri().toURL());
        CacheConfigurationBuilder<String, String> tenantTemplate = configuration
                .newCacheConfigurationBuilderFromTemplate("tenant-catalog", String.class, String.class);
        assertNotNull(tenantTemplate);

        try (CacheManager manager = CacheManagerBuilder.newCacheManager(configuration)) {
            manager.init();
            Cache<String, String> north = manager.getCache("north-catalog", String.class, String.class);
            assertNotNull(north);
            Map<String, String> northProducts = Map.of(
                    "coffee", "North roast | 1299",
                    "tea", "North tea selection | 799");
            north.putAll(northProducts);

            // A newly onboarded tenant inherits the template, not another tenant's entries.
            Cache<String, String> south = manager.createCache("south-catalog", tenantTemplate);
            assertNull(south.get("coffee"));
            Map<String, String> southProducts = Map.of(
                    "coffee", "South roast | 1499",
                    "cocoa", "South cocoa sampler | 599");
            south.putAll(southProducts);
            assertEquals(northProducts, north.getAll(northProducts.keySet()));
            assertEquals(southProducts, south.getAll(southProducts.keySet()));
            assertNull(north.get("cocoa"));
            assertNull(south.get("tea"));

            // Re-provisioning a retired tenant starts empty and leaves active tenants untouched.
            manager.removeCache("north-catalog");
            assertNull(manager.getCache("north-catalog", String.class, String.class));
            Cache<String, String> reopenedNorth = manager.createCache("north-catalog", tenantTemplate);
            assertNull(reopenedNorth.get("coffee"));
            assertNull(reopenedNorth.get("tea"));
            reopenedNorth.put("coffee", "North seasonal roast | 1199");
            assertEquals("North seasonal roast | 1199", reopenedNorth.get("coffee"));
            assertEquals(southProducts, south.getAll(southProducts.keySet()));
        }
    }
}
