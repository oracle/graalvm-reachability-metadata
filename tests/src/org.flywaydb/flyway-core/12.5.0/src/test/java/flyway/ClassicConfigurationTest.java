/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.util.Map;

import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassicConfigurationTest {

    @Test
    void configuresNestedConfigurationExtensionPropertiesFromFlatMap() {
        final ClassicConfiguration configuration = new ClassicConfiguration();

        configuration.configure(Map.of("flyway.integration.root.child.flag", "true"));

        final DynamicAccessConfigurationExtension extension = configuration.getConfigurationExtension(
                DynamicAccessConfigurationExtension.class);
        assertThat(extension).isNotNull();
        assertThat(extension.getRoot().getChild().getFlag()).isTrue();
    }
}
