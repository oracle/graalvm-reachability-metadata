/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.apache.velocity.texen.util.PropertiesUtil;
import org.junit.jupiter.api.Test;

public class PropertiesUtilTest {
    private static final String PROPERTIES_RESOURCE = "velocity/velocity/texen-test.properties";

    @Test
    public void loadsPropertiesResourceFromClasspath() throws Exception {
        ClasspathPropertiesUtil propertiesUtil = new ClasspathPropertiesUtil();

        Properties properties = propertiesUtil.loadClasspathResource(PROPERTIES_RESOURCE);

        assertThat(properties)
                .containsEntry("texen.resource.loaded", "true")
                .containsEntry("texen.resource.name", "PropertiesUtil");
    }

    private static final class ClasspathPropertiesUtil extends PropertiesUtil {
        private Properties loadClasspathResource(String propertiesFile) throws Exception {
            return loadFromClassPath(propertiesFile);
        }
    }
}
