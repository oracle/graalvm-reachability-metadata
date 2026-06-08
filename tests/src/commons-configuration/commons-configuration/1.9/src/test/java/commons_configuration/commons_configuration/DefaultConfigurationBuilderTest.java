/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_configuration.commons_configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.junit.jupiter.api.Test;

public class DefaultConfigurationBuilderTest {
    @Test
    public void constructorRegistersBuiltInConfigurationProviders() {
        DefaultConfigurationBuilder builder = new DefaultConfigurationBuilder();

        assertThat(DefaultConfigurationBuilder.ADDITIONAL_NAME)
                .isEqualTo(DefaultConfigurationBuilder.class.getName() + "/ADDITIONAL_CONFIG");
        assertThat(builder.providerForTag("properties")).isNotNull();
        assertThat(builder.providerForTag("xml"))
                .isInstanceOf(DefaultConfigurationBuilder.XMLConfigurationProvider.class);
        assertThat(builder.providerForTag("system")).isNotNull();
        assertThat(builder.providerForTag("configuration")).isNotNull();
    }
}
