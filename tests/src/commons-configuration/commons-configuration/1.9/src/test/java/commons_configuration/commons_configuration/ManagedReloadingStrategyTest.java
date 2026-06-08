/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_configuration.commons_configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.reloading.ManagedReloadingStrategy;
import org.junit.jupiter.api.Test;

public class ManagedReloadingStrategyTest {
    @Test
    public void refreshMarksReloadingAsRequiredUntilReloadingIsPerformed() {
        ManagedReloadingStrategy strategy = new ManagedReloadingStrategy();
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("application.name", "test");
        strategy.setConfiguration(configuration);

        assertThat(strategy.reloadingRequired()).isFalse();

        strategy.refresh();

        assertThat(strategy.reloadingRequired()).isTrue();

        strategy.reloadingPerformed();

        assertThat(strategy.reloadingRequired()).isFalse();
    }
}
