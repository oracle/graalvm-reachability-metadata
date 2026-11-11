/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.ServiceLoader;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.Configurator.ExecutionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RootLogLevelConfiguratorTest {
    @Test
    void discoversTheBootLogbackServiceAndAppliesItsDefaultRootLevel() {
        Configurator configurator = ServiceLoader.load(Configurator.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(candidate -> candidate.getClass().getName()
                        .equals("org.springframework.boot.logging.logback.RootLogLevelConfigurator"))
                .findFirst().orElseThrow();
        LoggerContext context = new LoggerContext();
        try {
            context.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.DEBUG);
            configurator.setContext(context);

            assertThat(configurator.configure(context)).isEqualTo(ExecutionStatus.INVOKE_NEXT_IF_ANY);
            assertThat(context.getLogger(Logger.ROOT_LOGGER_NAME).getLevel()).isEqualTo(Level.INFO);
        } finally {
            context.stop();
        }
    }
}
