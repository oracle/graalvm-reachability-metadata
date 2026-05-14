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
import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.status.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ContextInitializerTest {

  @Test
  void autoConfigReadsSelfDeclaredVersionResourcesWithoutMismatchWarnings() throws Exception {
    LoggerContext loggerContext = new LoggerContext();
    try {
      ContextInitializer initializer = new ContextInitializer(loggerContext);
      initializer.autoConfig(ContextInitializerTest.class.getClassLoader());

      List<Status> statuses = loggerContext.getStatusManager().getCopyOfStatusList();
      assertThat(statuses)
              .extracting(Status::getMessage)
              .anySatisfy(message -> assertThat(message)
                      .startsWith("Found logback-core version ")
                      .doesNotEndWith(" ?"));
      assertThat(statuses)
              .filteredOn(status -> status.getLevel() >= Status.WARN)
              .extracting(Status::getMessage)
              .noneMatch(message -> message.startsWith(
                      "Versions of logback-classic and logback-core are different"));
    } finally {
      loggerContext.stop();
    }
  }

  @Test
  void legacyJoranReflectionFallbackConfiguresFromClasspathResource() throws Throwable {
    LoggerContext loggerContext = new LoggerContext();
    try {
      ContextInitializer initializer = new ContextInitializer(loggerContext);
      Configurator.ExecutionStatus status = invokeLegacyJoranReflectionFallback(initializer);

      assertThat(status).isEqualTo(Configurator.ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY);
      Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
      Appender<ILoggingEvent> stdoutAppender = rootLogger.getAppender("STDOUT");
      assertThat(stdoutAppender).isNotNull();
      assertThat(stdoutAppender.isStarted()).isTrue();
    } finally {
      loggerContext.stop();
    }
  }

  private Configurator.ExecutionStatus invokeLegacyJoranReflectionFallback(ContextInitializer initializer)
          throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ContextInitializer.class, MethodHandles.lookup());
    MethodHandle methodHandle = lookup.findVirtual(ContextInitializer.class,
            "attemptConfigurationUsingJoranUsingReflexion",
            MethodType.methodType(Configurator.ExecutionStatus.class, ClassLoader.class));
    return (Configurator.ExecutionStatus) methodHandle.invoke(initializer,
            ContextInitializerTest.class.getClassLoader());
  }
}
