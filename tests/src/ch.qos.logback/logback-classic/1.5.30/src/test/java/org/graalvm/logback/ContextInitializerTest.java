/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.spi.LifeCycle;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class ContextInitializerTest {

  @Test
  void legacyJoranConfigurationPathLoadsDefaultConfigurator() throws Throwable {
    LoggerContext loggerContext = new LoggerContext();
    ContextInitializer initializer = new ContextInitializer(loggerContext);

    try {
      Configurator.ExecutionStatus status = invokeLegacyJoranConfiguration(initializer);

      Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
      Appender<?> stdoutAppender = rootLogger.getAppender("STDOUT");
      assertThat(status).isEqualTo(Configurator.ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY);
      assertThat(rootLogger.getLevel()).isEqualTo(Level.DEBUG);
      assertThat(stdoutAppender).isInstanceOf(LifeCycle.class);
      assertThat(((LifeCycle) stdoutAppender).isStarted()).isTrue();
    } finally {
      loggerContext.stop();
    }
  }

  private Configurator.ExecutionStatus invokeLegacyJoranConfiguration(ContextInitializer initializer) throws Throwable {
    MethodHandle methodHandle = MethodHandles.privateLookupIn(ContextInitializer.class, MethodHandles.lookup())
        .findVirtual(ContextInitializer.class, "attemptConfigurationUsingJoranUsingReflexion",
            MethodType.methodType(Configurator.ExecutionStatus.class, ClassLoader.class));
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    return (Configurator.ExecutionStatus) methodHandle.invoke(initializer, classLoader);
  }
}
