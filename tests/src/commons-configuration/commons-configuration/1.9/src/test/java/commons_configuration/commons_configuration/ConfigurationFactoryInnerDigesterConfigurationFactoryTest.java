/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_configuration.commons_configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.ConfigurationFactory;
import org.junit.jupiter.api.Test;

public class ConfigurationFactoryInnerDigesterConfigurationFactoryTest {
    @Test
    public void createObjectInstantiatesConfiguredConfigurationClass() throws Exception {
        ConfigurationFactory configurationFactory = new ConfigurationFactory();
        ConfigurationFactory.DigesterConfigurationFactory digesterFactory =
                configurationFactory.new DigesterConfigurationFactory(BaseConfiguration.class);

        Object configuration = digesterFactory.createObject(null);

        assertThat(configuration).isInstanceOf(BaseConfiguration.class);
        assertThat(((BaseConfiguration) configuration).isEmpty()).isTrue();
    }
}
