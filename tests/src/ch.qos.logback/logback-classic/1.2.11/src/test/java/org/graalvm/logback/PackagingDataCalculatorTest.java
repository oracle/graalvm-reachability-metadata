/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PackagingDataCalculatorTest {

  @Test
  void resolvesPackagingDataForLogbackFrames() {
    Thread thread = Thread.currentThread();
    ClassLoader originalClassLoader = thread.getContextClassLoader();
    LoggerContext context = new LoggerContext();
    try {
      context.setPackagingDataEnabled(true);
      Logger logger = context.getLogger(PackagingDataCalculatorTest.class);
      thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());

      IllegalStateException failure = new IllegalStateException("failure");
      failure.setStackTrace(new StackTraceElement[] {
          new StackTraceElement(Logger.class.getName(), "error", "Logger.java", 1)
      });
      LoggingEvent event = new LoggingEvent(PackagingDataCalculatorTest.class.getName(), logger, Level.ERROR,
          "operation failed", failure, null);
      IThrowableProxy proxy = event.getThrowableProxy();

      assertThat(proxy).isNotNull();
      assertThat(proxy.getStackTraceElementProxyArray()).singleElement()
          .satisfies(PackagingDataCalculatorTest::assertResolvedFrame);
    } finally {
      thread.setContextClassLoader(originalClassLoader);
      context.stop();
    }
  }

  private static void assertResolvedFrame(StackTraceElementProxy frame) {
    assertThat(frame.getStackTraceElement().getClassName()).isEqualTo(Logger.class.getName());
    assertThat(frame.getClassPackagingData()).isNotNull();
    assertThat(frame.getClassPackagingData().getCodeLocation()).isNotBlank();
  }
}
