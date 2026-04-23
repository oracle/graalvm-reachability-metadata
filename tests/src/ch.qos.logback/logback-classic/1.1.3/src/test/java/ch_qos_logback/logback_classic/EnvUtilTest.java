/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_classic;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.util.EnvUtil;
import org.junit.jupiter.api.Test;

public class EnvUtilTest {

    @Test
    void reportsGroovyAvailabilityWhenBindingClassIsResolvable() {
        assertThat(EnvUtil.isGroovyAvailable()).isTrue();
    }

    @Test
    void consultsTheServiceLoader() {
        EnvUtil.loadFromServiceLoader(Configurator.class);

        assertThat(EnvUtil.isServiceLoaderAvailable()).isTrue();
    }
}
