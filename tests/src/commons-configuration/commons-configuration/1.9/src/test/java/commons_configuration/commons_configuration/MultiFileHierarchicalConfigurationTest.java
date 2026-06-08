/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_configuration.commons_configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration.MultiFileHierarchicalConfiguration;
import org.junit.jupiter.api.Test;

public class MultiFileHierarchicalConfigurationTest {
    @Test
    public void propertyAccessUsesCachedConfigurationForFilePattern() {
        MultiFileHierarchicalConfiguration configuration = new MultiFileHierarchicalConfiguration();
        configuration.setFilePattern("tenant-a.xml");

        configuration.addProperty("service.name", "orders");

        assertThat(configuration.containsKey("service.name")).isTrue();
        assertThat(configuration.getString("service.name")).isEqualTo("orders");
    }

    @Test
    public void constructorWithFilePatternInitializesConfigurationAccess() {
        MultiFileHierarchicalConfiguration configuration = new MultiFileHierarchicalConfiguration("tenant-b.xml");

        configuration.addProperty("service.port", 8080);

        assertThat(configuration.getInt("service.port")).isEqualTo(8080);
    }
}
