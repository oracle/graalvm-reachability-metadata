/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_configuration.commons_configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.DynamicCombinedConfiguration;
import org.apache.commons.configuration.tree.UnionCombiner;
import org.junit.jupiter.api.Test;

public class DynamicCombinedConfigurationTest {
    @Test
    public void constructorWithCombinerInitializesDynamicConfiguration() {
        DynamicCombinedConfiguration configuration = new DynamicCombinedConfiguration(new UnionCombiner());

        configuration.setKeyPattern("default-tenant");

        assertThat(configuration.getKeyPattern()).isEqualTo("default-tenant");
        assertThat(configuration.getNodeCombiner()).isInstanceOf(UnionCombiner.class);
    }

    @Test
    public void propertyAccessUsesConfigurationSelectedByKeyPattern() {
        DynamicCombinedConfiguration configuration = new DynamicCombinedConfiguration(new UnionCombiner());
        configuration.setKeyPattern("tenant-a");
        BaseConfiguration child = new BaseConfiguration();
        child.addProperty("service.url", "https://service.example.test");

        configuration.addConfiguration(child, "service", null);

        assertThat(configuration.getNumberOfConfigurations()).isEqualTo(1);
        assertThat(configuration.getConfiguration("service")).isSameAs(child);
        assertThat(configuration.getString("service.url")).isEqualTo("https://service.example.test");
    }
}
