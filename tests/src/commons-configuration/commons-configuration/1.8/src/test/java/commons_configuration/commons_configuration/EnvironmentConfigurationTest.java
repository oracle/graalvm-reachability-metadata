/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_configuration.commons_configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Iterator;
import java.util.Map;

import org.apache.commons.configuration.EnvironmentConfiguration;
import org.apache.commons.lang.SystemUtils;
import org.junit.jupiter.api.Test;

public class EnvironmentConfigurationTest {
    private static final int JAVA_1_5 = 150;

    @Test
    public void constructorReadsProcessEnvironmentThroughSystemGetenv() {
        String originalJavaVersion = useJavaVersionRecognizedByCommonsLang();
        try {
            assertThat(SystemUtils.isJavaVersionAtLeast(JAVA_1_5)).isTrue();

            EnvironmentConfiguration configuration = new EnvironmentConfiguration();
            configuration.setDelimiterParsingDisabled(true);

            assertThat(configuration.isEmpty()).isEqualTo(System.getenv().isEmpty());
            assertThat(configuration.getKeys().hasNext()).isEqualTo(!System.getenv().isEmpty());
            for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
                assertThat(configuration.containsKey(entry.getKey())).isTrue();
                assertThat(configuration.getProperty(entry.getKey())).isEqualTo(entry.getValue());
            }
        } finally {
            restoreJavaVersionString(originalJavaVersion);
        }
    }

    @Test
    public void environmentConfigurationIsReadOnly() {
        String originalJavaVersion = useJavaVersionRecognizedByCommonsLang();
        try {
            EnvironmentConfiguration configuration = new EnvironmentConfiguration();

            assertThatThrownBy(configuration::clear).isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("EnvironmentConfiguration is read-only!");
            assertThatThrownBy(() -> configuration.clearProperty("PATH"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("EnvironmentConfiguration is read-only!");
        } finally {
            restoreJavaVersionString(originalJavaVersion);
        }
    }

    @Test
    public void getKeysReturnsEachEnvironmentVariableName() {
        String originalJavaVersion = useJavaVersionRecognizedByCommonsLang();
        try {
            EnvironmentConfiguration configuration = new EnvironmentConfiguration();
            Iterator keys = configuration.getKeys();

            while (keys.hasNext()) {
                assertThat(System.getenv()).containsKey((String) keys.next());
            }
        } finally {
            restoreJavaVersionString(originalJavaVersion);
        }
    }

    private static String useJavaVersionRecognizedByCommonsLang() {
        // Commons Lang 2.x recognizes pre-Java-9 version strings in this compatibility check.
        String originalJavaVersion = System.getProperty("java.version");
        System.setProperty("java.version", "1.8.0");
        return originalJavaVersion;
    }

    private static void restoreJavaVersionString(String originalJavaVersion) {
        if (originalJavaVersion == null) {
            System.clearProperty("java.version");
        } else {
            System.setProperty("java.version", originalJavaVersion);
        }
    }
}
