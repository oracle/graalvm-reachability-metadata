/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import java.util.Properties;

import org.apache.velocity.texen.util.PropertiesUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PropertiesUtilTest {
    private static final String TEXEN_DEFAULTS = "org/apache/velocity/texen/defaults/texen.properties";

    @Test
    void loadsPropertiesResourceFromClasspath() {
        PropertiesUtil propertiesUtil = new PropertiesUtil();

        Properties properties = propertiesUtil.load(TEXEN_DEFAULTS);

        assertNotNull(properties);
        assertEquals("org.apache.velocity.texen.util.PropertiesUtil", properties.getProperty("context.objects.properties"));
    }
}
