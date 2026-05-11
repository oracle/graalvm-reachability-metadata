/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Properties;

import org.apache.velocity.texen.util.PropertiesUtil;
import org.junit.jupiter.api.Test;

public class PropertiesUtilTest {
    @Test
    void looksForPropertiesOnClasspath() {
        final ClasspathPropertiesUtil propertiesUtil = new ClasspathPropertiesUtil();

        assertThatNullPointerException()
                .isThrownBy(() -> propertiesUtil.loadClasspathProperties("velocity/velocity_dep/missing.properties"));
    }

    private static final class ClasspathPropertiesUtil extends PropertiesUtil {
        private Properties loadClasspathProperties(final String propertiesFile) {
            return loadFromClassPath(propertiesFile);
        }
    }
}
