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
import ch.qos.logback.core.status.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ContextInitializerTest {

  @Test
  void shouldAutoConfigureWithDefaultJoranConfiguratorAndVersionResources() throws Exception {
    LoggerContext context = new LoggerContext();
    context.setName("context-initializer-auto-config-test");

    try {
      ContextInitializer initializer = new ContextInitializer(context);
      initializer.autoConfig(ContextInitializerTest.class.getClassLoader());

      assertThat(context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("STDOUT")).isNotNull();
      assertThat(context.getStatusManager().getCopyOfStatusList())
          .anyMatch(status -> status.getMessage().startsWith("Found logback-core version "))
          .noneMatch(status -> status.getLevel() >= Status.WARN)
          .noneMatch(status -> status.getMessage().contains("Versions of logback-core and logback-classic"));
    } finally {
      context.stop();
    }
  }

  @Test
  void shouldExerciseLegacyJoranReflectionConfigurationPath() throws Exception {
    LoggerContext context = new LoggerContext();
    context.setName("context-initializer-legacy-reflection-test");

    try {
      ContextInitializer initializer = new ContextInitializer(context);
      Configurator.ExecutionStatus status = invokeLegacyJoranReflectionPath(initializer);

      assertThat(status).isEqualTo(Configurator.ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY);
      assertThat(context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("STDOUT")).isNotNull();
    } finally {
      context.stop();
    }
  }

  private Configurator.ExecutionStatus invokeLegacyJoranReflectionPath(ContextInitializer initializer) throws Exception {
    Method method = ContextInitializer.class.getDeclaredMethod(
        "attemptConfigurationUsingJoranUsingReflexion", ClassLoader.class);
    method.setAccessible(true);
    return (Configurator.ExecutionStatus) method.invoke(initializer, ContextInitializerTest.class.getClassLoader());
  }
}
