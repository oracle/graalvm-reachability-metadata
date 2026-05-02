/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ConfigurationFactoryTest {
    private static final String DEFAULT_CLASSPATH_CONFIGURATION_FILE = "/ehcache.xml";

    @TempDir
    Path tempDirectory;

    @Test
    void parsesConfigurationDiscoveredByContextClassLoader() throws Exception {
        Path configurationFile = tempDirectory.resolve("ehcache-context-loader.xml");
        Files.writeString(configurationFile, """
                <ehcache name="context-loader-configuration"
                         updateCheck="false"
                         monitoring="off">
                    <diskStore path="java.io.tmpdir/ehcache-context-loader"/>
                    <defaultCache maxElementsInMemory="7"
                                  eternal="false"
                                  overflowToDisk="false"
                                  timeToIdleSeconds="11"
                                  timeToLiveSeconds="13"/>
                </ehcache>
                """, StandardCharsets.UTF_8);

        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader resourceClassLoader = new EhcacheXmlClassLoader(
                originalContextClassLoader,
                configurationFile.toUri().toURL());
        Thread.currentThread().setContextClassLoader(resourceClassLoader);
        try {
            Configuration configuration = ConfigurationFactory.parseConfiguration();

            assertThat(configuration.getName()).isEqualTo("context-loader-configuration");
            assertThat(configuration.getDiskStoreConfiguration().getOriginalPath())
                    .isEqualTo("java.io.tmpdir/ehcache-context-loader");
            assertThat(configuration.getDefaultCacheConfiguration().getMaxElementsInMemory()).isEqualTo(7);
            assertThat(configuration.getDefaultCacheConfiguration().getTimeToIdleSeconds()).isEqualTo(11);
            assertThat(configuration.getDefaultCacheConfiguration().getTimeToLiveSeconds()).isEqualTo(13);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class EhcacheXmlClassLoader extends ClassLoader {
        private final URL configurationUrl;

        private EhcacheXmlClassLoader(ClassLoader parent, URL configurationUrl) {
            super(parent);
            this.configurationUrl = configurationUrl;
        }

        @Override
        public URL getResource(String name) {
            if (DEFAULT_CLASSPATH_CONFIGURATION_FILE.equals(name)) {
                return configurationUrl;
            }
            return super.getResource(name);
        }
    }
}
