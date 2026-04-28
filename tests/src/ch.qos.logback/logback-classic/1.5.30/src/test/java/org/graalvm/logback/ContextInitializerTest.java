/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.lang.reflect.Method;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ContextInitializerTest {

  private final LoggerContext loggerContext = new LoggerContext();

  @Test
  void legacyJoranConfigurationPathLoadsAndInstantiatesDefaultConfigurator() throws Exception {
    ContextInitializer contextInitializer = new ContextInitializer(loggerContext);
    Method method = ContextInitializer.class.getDeclaredMethod(
            "attemptConfigurationUsingJoranUsingReflexion", ClassLoader.class);
    method.setAccessible(true);

    Configurator.ExecutionStatus status = (Configurator.ExecutionStatus) method.invoke(
            contextInitializer, ContextInitializer.class.getClassLoader());

    Appender<?> appender = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("STDOUT");
    assertThat(status).isEqualTo(Configurator.ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY);
    assertThat(appender).isNotNull();
  }

  @AfterEach
  void tearDown() {
    loggerContext.stop();
  }
}
