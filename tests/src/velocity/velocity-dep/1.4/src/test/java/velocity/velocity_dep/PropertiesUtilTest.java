/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import org.apache.velocity.texen.util.PropertiesUtil;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertiesUtilTest {
    @Test
    void loadsPropertiesResourceFromClasspath() {
        ClasspathPropertiesUtil propertiesUtil = new ClasspathPropertiesUtil();

        Properties properties = propertiesUtil.loadResource(
                "$generator.templatePath/velocity/velocity_dep/properties-util-classpath.properties");

        assertThat(properties.getProperty("message")).isEqualTo("loaded from classpath");
        assertThat(properties.getProperty("component")).isEqualTo("texen-properties-util");
    }
}

final class ClasspathPropertiesUtil extends PropertiesUtil {
    Properties loadResource(String resourceName) {
        return loadFromClassPath(resourceName);
    }
}
