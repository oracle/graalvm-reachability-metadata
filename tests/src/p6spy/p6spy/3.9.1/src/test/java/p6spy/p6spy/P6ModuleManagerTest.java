/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package p6spy.p6spy;

import static org.assertj.core.api.Assertions.assertThat;

import com.p6spy.engine.spy.P6ModuleManager;
import com.p6spy.engine.spy.P6SpyDriver;
import com.p6spy.engine.spy.P6SpyOptions;
import org.junit.jupiter.api.Test;

public class P6ModuleManagerTest {
    @Test
    void explicitlyLoadsConfiguredJdbcDriver() {
        String propertyName = "p6spy.config.driverlist";
        String originalValue = System.getProperty(propertyName);
        System.setProperty(propertyName, P6SpyDriver.class.getName());

        try {
            P6ModuleManager.getInstance().reload();

            assertThat(P6SpyOptions.getActiveInstance().getDriverNames())
                    .containsExactly(P6SpyDriver.class.getName());
            assertThat(new P6SpyDriver().acceptsURL("jdbc:p6spy:example")).isTrue();
        } finally {
            if (originalValue == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, originalValue);
            }
            P6ModuleManager.getInstance().reload();
        }
    }
}
