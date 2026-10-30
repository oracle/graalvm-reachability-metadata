/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.nio.file.Path;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationHelper;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class ConfigurationHelperTest {
    @TempDir
    Path directory;

    @Test
    void buildsConfiguredCachesAndDefaultTemplate() {
        Configuration configuration = TestSupport.configuration(directory);
        CacheConfiguration named = new CacheConfiguration();
        named.setName("configured");
        named.setMaxElementsInMemory(9);
        named.setMemoryStoreEvictionPolicy("FIFO");
        named.setEternal(true);
        configuration.addCache(named);
        CacheManager manager = new CacheManager(configuration);
        try {
            ConfigurationHelper helper = new ConfigurationHelper(manager, configuration);
            assertEquals(directory.toString(), helper.getDiskStorePath());
            assertEquals(20, helper.createDefaultCache().getMaxElementsInMemory());
            Cache cache = manager.getCache("configured");
            cache.put(new Element("key", "value"));
            assertEquals("value", cache.get("key").getObjectValue());
            assertEquals(9, cache.getMaxElementsInMemory());
            assertEquals(MemoryStoreEvictionPolicy.FIFO, cache.getMemoryStoreEvictionPolicy());
        } finally {
            manager.shutdown();
        }
    }
}
