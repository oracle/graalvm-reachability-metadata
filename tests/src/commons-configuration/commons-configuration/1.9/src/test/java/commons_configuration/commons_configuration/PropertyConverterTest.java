/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_configuration.commons_configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.DataConfiguration;
import org.apache.commons.configuration.PropertyConverter;
import org.junit.jupiter.api.Test;

public class PropertyConverterTest {
    @Test
    public void toIntegerReturnsExistingNumericValue() {
        assertThat(PropertyConverter.toInteger(Integer.valueOf(8080))).isEqualTo(Integer.valueOf(8080));
    }

    @Test
    public void dataConfigurationConvertsStringValueToEnumByValueOf() {
        String originalJavaVersion = useLegacyJavaVersionString();
        try {
            BaseConfiguration configuration = new BaseConfiguration();
            configuration.addProperty("mode", "PRODUCTION");
            DataConfiguration dataConfiguration = new DataConfiguration(configuration);

            assertThat(dataConfiguration.get(DeploymentMode.class, "mode")).isEqualTo(DeploymentMode.PRODUCTION);
        } finally {
            restoreJavaVersionString(originalJavaVersion);
        }
    }

    @Test
    public void dataConfigurationConvertsNumericValueToEnumByValuesArray() {
        String originalJavaVersion = useLegacyJavaVersionString();
        try {
            BaseConfiguration configuration = new BaseConfiguration();
            configuration.addProperty("mode", Integer.valueOf(2));
            DataConfiguration dataConfiguration = new DataConfiguration(configuration);

            assertThat(dataConfiguration.get(DeploymentMode.class, "mode")).isEqualTo(DeploymentMode.MAINTENANCE);
        } finally {
            restoreJavaVersionString(originalJavaVersion);
        }
    }

    private static String useLegacyJavaVersionString() {
        // Commons Lang 2.x only recognizes pre-Java-9 version strings in this compatibility check.
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

    public enum DeploymentMode {
        DEVELOPMENT,
        PRODUCTION,
        MAINTENANCE
    }
}
