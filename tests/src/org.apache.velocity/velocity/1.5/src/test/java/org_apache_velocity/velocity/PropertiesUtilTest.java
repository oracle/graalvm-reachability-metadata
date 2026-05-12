/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.apache.velocity.texen.util.PropertiesUtil;
import org.junit.jupiter.api.Test;

public class PropertiesUtilTest {
    private static final String PROPERTIES_RESOURCE = "org_apache_velocity/velocity/properties-util.properties";

    @Test
    void loadsPropertiesFromClasspathResource() throws Exception {
        ClasspathPropertiesUtil propertiesUtil = new ClasspathPropertiesUtil();

        Properties properties = propertiesUtil.loadClasspathProperties(PROPERTIES_RESOURCE);

        assertThat(properties).containsEntry("texen.resource", "loaded from classpath");
    }

    private static final class ClasspathPropertiesUtil extends PropertiesUtil {
        private Properties loadClasspathProperties(String propertiesFile) throws Exception {
            return loadFromClassPath(propertiesFile);
        }
    }
}
