/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.nio.file.Files;
import java.nio.file.Path;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class ConfigurationFactoryTest {
    @TempDir
    Path directory;

    @Test
    void loadsBundledFailsafeConfiguration() {
        Configuration configuration = ConfigurationFactory.parseConfiguration();
        assertNotNull(configuration.getDefaultCacheConfiguration());
        assertTrue(configuration.getDefaultCacheConfiguration().getMaxElementsInMemory() > 0);
        assertNotNull(configuration.getDiskStoreConfiguration().getPath());
    }

    @Test
    void loadsConfigurationFromFileAndUrl() throws Exception {
        Path file = directory.resolve("cache.xml");
        Files.writeString(file, """
                <ehcache>
                  <defaultCache maxElementsInMemory="13" eternal="true" overflowToDisk="false"/>
                </ehcache>
                """);
        assertEquals(13, ConfigurationFactory.parseConfiguration(file.toFile())
                .getDefaultCacheConfiguration().getMaxElementsInMemory());
        assertEquals(13, ConfigurationFactory.parseConfiguration(file.toUri().toURL())
                .getDefaultCacheConfiguration().getMaxElementsInMemory());
    }
}
