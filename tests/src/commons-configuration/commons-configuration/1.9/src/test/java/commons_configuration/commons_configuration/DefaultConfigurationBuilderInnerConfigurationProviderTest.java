/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_configuration.commons_configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.junit.jupiter.api.Test;

public class DefaultConfigurationBuilderInnerConfigurationProviderTest {
    @Test
    public void fetchConfigurationClassResolvesConfiguredClassName() throws Exception {
        ExposedConfigurationProvider provider =
                new ExposedConfigurationProvider(BaseConfiguration.class.getName());

        Class configurationClass = provider.resolveConfigurationClass();

        assertThat(configurationClass).isEqualTo(BaseConfiguration.class);
        assertThat(provider.getConfigurationClass()).isSameAs(BaseConfiguration.class);
    }

    private static final class ExposedConfigurationProvider
            extends DefaultConfigurationBuilder.ConfigurationProvider {
        ExposedConfigurationProvider(String configurationClassName) {
            super(configurationClassName);
        }

        Class resolveConfigurationClass() throws Exception {
            return fetchConfigurationClass();
        }
    }
}
