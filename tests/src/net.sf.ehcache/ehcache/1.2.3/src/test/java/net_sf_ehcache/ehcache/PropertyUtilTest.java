/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.util.Map;
import java.util.Properties;
import net.sf.ehcache.util.PropertyUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class PropertyUtilTest {
    @Test
    void parsesCommaSeparatedFactoryPropertiesAndTrimsValues() {
        Properties properties = PropertyUtil.parseProperties("peerDiscovery=manual, enabled= true " + ",capacity=25");
        assertEquals("manual", PropertyUtil.extractAndLogProperty("peerDiscovery", properties));
        assertEquals("25", PropertyUtil.extractAndLogProperty("capacity", properties));
        assertTrue(PropertyUtil.parseBoolean(PropertyUtil.extractAndLogProperty("enabled", properties)));
        assertEquals("value", PropertyUtil.extractAndLogProperty("key", Map.of("key", " value ")));
        assertNull(PropertyUtil.extractAndLogProperty("absent", properties));
        assertFalse(PropertyUtil.parseBoolean(null));
    }
}
