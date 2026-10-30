/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import java.util.Properties;

import org.apache.velocity.texen.util.PropertiesUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PropertiesUtilTest {
    @Test
    void loadsGenerationPropertiesFromClasspath() {
        Properties properties = new PropertiesUtil().load("velocity/settings.properties");
        assertEquals("Atlas", properties.getProperty("projectName"));
        assertEquals("4", properties.getProperty("itemCount"));
    }
}
