/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.lang.reflect.InvocationTargetException;

import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClassReloadingStrategyTest {
    @Test
    void automaticStrategyRequiresAnInstalledAgent() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                ClassReloadingStrategy::fromInstalledAgent);

        assertThat(exception).hasCauseInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("The Byte Buddy agent is not loaded or this method is not called via the system class loader");
    }

    @Test
    void explicitStrategiesRequireAnInstalledAgent() {
        for (ClassReloadingStrategy.Strategy strategy : ClassReloadingStrategy.Strategy.values()) {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> ClassReloadingStrategy.fromInstalledAgent(strategy));

            assertThat(exception).hasCauseInstanceOf(InvocationTargetException.class)
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("The Byte Buddy agent is not loaded or this method is not called via the system class loader");
        }
    }
}
