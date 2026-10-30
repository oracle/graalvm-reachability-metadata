/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Timeout(120)
public class BeanHandlerTest {
    @Test
    void parsesNestedConfigurationAndFactoryBeans() {
        String xml = """
                <ehcache>
                  <diskStore path="java.io.tmpdir"/>
                  <defaultCache maxElementsInMemory="20" eternal="true" overflowToDisk="false"/>
                  <cache name="orders" maxElementsInMemory="7" eternal="false" overflowToDisk="false"
                         timeToLiveSeconds="90" timeToIdleSeconds="30" diskExpiryThreadIntervalSeconds="120">
                    <cacheEventListenerFactory class="net.sf.ehcache.distribution.RMICacheReplicatorFactory"
                                               properties="replicateAsynchronously=false"/>
                    <bootstrapCacheLoaderFactory class="net.sf.ehcache.distribution.RMIBootstrapCacheLoaderFactory"
                                                 properties="bootstrapAsynchronously=false"/>
                  </cache>
                </ehcache>
                """;
        Configuration configuration = ConfigurationFactory.parseConfiguration(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        CacheConfiguration cache = (CacheConfiguration) configuration.getCacheConfigurations().get("orders");
        assertEquals(7, cache.getMaxElementsInMemory());
        assertEquals(90, cache.getTimeToLiveSeconds());
        assertEquals(30, cache.getTimeToIdleSeconds());
        assertEquals(120, cache.getDiskExpiryThreadIntervalSeconds());
        assertFalse(cache.isEternal());
        assertEquals(1, cache.getCacheEventListenerConfigurations().size());
        assertEquals("net.sf.ehcache.distribution.RMIBootstrapCacheLoaderFactory",
                cache.getBootstrapCacheLoaderFactoryConfiguration().getFullyQualifiedClassPath());
        assertEquals(System.getProperty("java.io.tmpdir"), configuration.getDiskStoreConfiguration().getPath());
    }
}
