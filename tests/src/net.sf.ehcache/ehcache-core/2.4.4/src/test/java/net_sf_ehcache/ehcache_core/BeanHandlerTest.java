/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationFactory;
import net.sf.ehcache.config.Searchable;

import org.junit.jupiter.api.Test;

public class BeanHandlerTest {
    @Test
    void parsesNestedConfigurationThroughBeanHandler() {
        Configuration configuration = parseConfiguration("""
                <ehcache name="bean-handler-coverage"
                         updateCheck="false"
                         monitoring="off"
                         dynamicConfig="true"
                         defaultTransactionTimeoutInSeconds="17">
                    <diskStore path="java.io.tmpdir/ehcache-bean-handler"/>
                    <transactionManagerLookup class="java.lang.String"
                                              properties="lookup=true"
                                              propertySeparator="|"/>
                    <cacheManagerPeerProviderFactory class="java.lang.String"
                                                     properties="peer=provider"/>
                    <cacheManagerPeerListenerFactory class="java.lang.String"
                                                     properties="peer=listener"/>
                    <cacheManagerEventListenerFactory class="java.lang.String"
                                                      properties="manager=listener"/>
                    <terracottaConfig rejoin="false">
                        <tc:tc-config xmlns:tc="http://www.terracotta.org/config">
                            <servers>
                                <server host="localhost" name="test-server"/>
                            </servers>
                        </tc:tc-config>
                    </terracottaConfig>
                    <defaultCache maxElementsInMemory="10"
                                  eternal="false"
                                  overflowToDisk="false"
                                  timeToIdleSeconds="1"
                                  timeToLiveSeconds="2"/>
                    <cache name="dynamicAccessCache"
                           maxElementsInMemory="100"
                           eternal="false"
                           overflowToDisk="false"
                           timeToIdleSeconds="60"
                           timeToLiveSeconds="120"
                           diskPersistent="false"
                           diskExpiryThreadIntervalSeconds="5"
                           memoryStoreEvictionPolicy="LRU"
                           clearOnFlush="true"
                           copyOnRead="false"
                           copyOnWrite="false"
                           transactionalMode="off">
                        <cacheEventListenerFactory class="java.lang.String" properties="event=listener"/>
                        <cacheExtensionFactory class="java.lang.String" properties="extension=true"/>
                        <bootstrapCacheLoaderFactory class="java.lang.String" properties="bootstrap=true"/>
                        <cacheExceptionHandlerFactory class="java.lang.String" properties="handler=true"/>
                        <cacheLoaderFactory class="java.lang.String" properties="loader=true"/>
                        <cacheDecoratorFactory class="java.lang.String" properties="decorator=true"/>
                        <searchable keys="false" values="false">
                            <searchAttribute name="title" expression="value.title"/>
                        </searchable>
                        <terracotta copyOnRead="false"
                                    coherentReads="true"
                                    localKeyCache="false"
                                    localKeyCacheSize="16"
                                    orphanEviction="true"
                                    orphanEvictionPeriod="4"
                                    synchronousWrites="false"
                                    valueMode="serialization"
                                    storageStrategy="classic">
                            <nonstop timeoutMillis="2500"
                                     immediateTimeout="false"
                                     bulkOpsTimeoutMultiplyFactor="3">
                                <timeoutBehavior type="noop"/>
                            </nonstop>
                        </terracotta>
                        <cacheWriter writeMode="write-through"
                                     notifyListenersOnException="true"
                                     minWriteDelay="1"
                                     maxWriteDelay="2"
                                     rateLimitPerSecond="3"
                                     writeCoalescing="true"
                                     writeBatching="true"
                                     writeBatchSize="4"
                                     retryAttempts="2"
                                     retryAttemptDelaySeconds="1"
                                     writeBehindMaxQueueSize="8">
                            <cacheWriterFactory class="java.lang.String" properties="writer=true"/>
                        </cacheWriter>
                    </cache>
                </ehcache>
                """);

        assertThat(configuration.getName()).isEqualTo("bean-handler-coverage");
        assertThat(configuration.getUpdateCheck()).isFalse();
        assertThat(configuration.getDefaultTransactionTimeoutInSeconds()).isEqualTo(17);
        assertThat(configuration.getDiskStoreConfiguration().getOriginalPath())
                .isEqualTo("java.io.tmpdir/ehcache-bean-handler");
        assertThat(configuration.getCacheManagerPeerProviderFactoryConfiguration()).hasSize(1);
        assertThat(configuration.getCacheManagerPeerListenerFactoryConfigurations()).hasSize(1);
        assertThat(configuration.getCacheManagerEventListenerFactoryConfiguration().getProperties())
                .isEqualTo("manager=listener");
        assertThat(configuration.getTerracottaConfiguration().getOriginalEmbeddedConfig())
                .contains("<servers>")
                .contains("test-server");

        CacheConfiguration cache = configuration.getCacheConfigurations().get("dynamicAccessCache");
        assertThat(cache).isNotNull();
        assertThat(cache.getMaxElementsInMemory()).isEqualTo(100);
        assertThat(cache.getTimeToLiveSeconds()).isEqualTo(120);
        assertThat(cache.getCacheEventListenerConfigurations()).hasSize(1);
        assertThat(cache.getCacheWriterConfiguration().getCacheWriterFactoryConfiguration().getProperties())
                .isEqualTo("writer=true");
        assertThat(cache.getTerracottaConfiguration().getNonstopConfiguration().getTimeoutBehavior().getType())
                .isEqualTo("noop");

        Searchable searchable = cache.getSearchable();
        assertThat(searchable).isNotNull();
        assertThat(searchable.getUserDefinedSearchAttributes()).containsKey("title");
    }

    private static Configuration parseConfiguration(String xml) {
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        InputStream inputStream = new ByteArrayInputStream(bytes);
        return ConfigurationFactory.parseConfiguration(inputStream);
    }
}
