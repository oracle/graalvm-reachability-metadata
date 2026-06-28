/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationFactory;
import net.sf.ehcache.config.SearchAttribute;
import net.sf.ehcache.config.Searchable;
import net.sf.ehcache.config.TerracottaClientConfiguration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BeanHandlerTest {
    @TempDir
    Path diskStoreDirectory;

    @BeforeAll
    static void disableUpdateChecks() {
        System.setProperty("net.sf.ehcache.skipUpdateCheck", "true");
    }

    @Test
    void parsesXmlConfigurationElementsAttributesAndExtractedSubtree() {
        String xml = """
                <ehcache name="bean-handler-test" updateCheck="false" monitoring="off"
                         dynamicConfig="true" defaultTransactionTimeoutInSeconds="45">
                    <diskStore path="%s"/>
                    <terracottaConfig rejoin="false">
                        <tc-config>
                            <servers>
                                <server host="localhost" name="server0">
                                    <data>target/server-data</data>
                                </server>
                            </servers>
                        </tc-config>
                    </terracottaConfig>
                    <defaultCache maxElementsInMemory="10" eternal="false" overflowToDisk="false"
                                  timeToIdleSeconds="5" timeToLiveSeconds="10">
                        <searchable keys="true" values="false">
                            <searchAttribute name="title" expression="value.getTitle()"/>
                        </searchable>
                    </defaultCache>
                    <cache name="sample" maxElementsInMemory="20" eternal="true" overflowToDisk="false"
                           diskPersistent="false"/>
                </ehcache>
                """.formatted(diskStoreDirectory.toAbsolutePath());

        Configuration configuration = ConfigurationFactory.parseConfiguration(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertThat(configuration.getName()).isEqualTo("bean-handler-test");
        assertThat(configuration.getUpdateCheck()).isFalse();
        assertThat(configuration.getMonitoring()).isEqualTo(Configuration.Monitoring.OFF);
        assertThat(configuration.getDynamicConfig()).isTrue();
        assertThat(configuration.getDefaultTransactionTimeoutInSeconds()).isEqualTo(45);
        assertThat(configuration.getDiskStoreConfiguration().getPath())
                .isEqualTo(diskStoreDirectory.toAbsolutePath().toString());

        TerracottaClientConfiguration terracotta = configuration.getTerracottaConfiguration();
        assertThat(terracotta.isRejoin()).isFalse();
        assertThat(terracotta.getOriginalEmbeddedConfig()).contains("<servers>", "server0", "target/server-data");

        CacheConfiguration defaultCache = configuration.getDefaultCacheConfiguration();
        assertThat(defaultCache.getMaxElementsInMemory()).isEqualTo(10);
        assertThat(defaultCache.isEternal()).isFalse();
        assertThat(defaultCache.getTimeToIdleSeconds()).isEqualTo(5);
        assertThat(defaultCache.getTimeToLiveSeconds()).isEqualTo(10);
        assertThat(defaultCache.isOverflowToDisk()).isFalse();

        Searchable searchable = defaultCache.getSearchable();
        SearchAttribute searchAttribute = searchable.getSearchAttributes().get("title");
        assertThat(searchAttribute.getExpression()).isEqualTo("value.getTitle()");

        CacheConfiguration namedCache = configuration.getCacheConfigurations().get("sample");
        assertThat(namedCache.getName()).isEqualTo("sample");
        assertThat(namedCache.getMaxElementsInMemory()).isEqualTo(20);
        assertThat(namedCache.isEternal()).isTrue();
        assertThat(namedCache.isOverflowToDisk()).isFalse();
        assertThat(namedCache.isDiskPersistent()).isFalse();
    }
}
