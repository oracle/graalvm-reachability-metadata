/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.io.File;
import java.util.List;
import net.sf.ehcache.config.DiskStoreConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class DiskStoreConfigurationTest {
    @Test
    void expandsSupportedDirectoryTokens() {
        DiskStoreConfiguration configuration = new DiskStoreConfiguration();
        for (String property : List.of("java.io.tmpdir", "user.home", "user.dir")) {
            configuration.setPath(property + File.separator + "cache-data");
            assertEquals(System.getProperty(property) + File.separator + "cache-data", configuration.getPath());
        }
    }
}
