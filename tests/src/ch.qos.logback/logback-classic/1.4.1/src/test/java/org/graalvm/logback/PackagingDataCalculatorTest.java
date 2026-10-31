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
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PackagingDataCalculatorTest {

  @Test
  void calculatesPackagingDataWhenContextClassLoaderCannotLoadApplicationFrames() {
    Thread currentThread = Thread.currentThread();
    ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    LoggerContext loggerContext = new LoggerContext();
    try {
      loggerContext.setName("packaging-data-calculator-test");
      loggerContext.setPackagingDataEnabled(true);
      Logger logger = loggerContext.getLogger(PackagingDataCalculatorTest.class);
      Throwable throwable = createThrowableWithApplicationStackFrame();

      currentThread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
      ILoggingEvent loggingEvent = new LoggingEvent(PackagingDataCalculatorTest.class.getName(), logger, Level.ERROR,
          "packaging data", throwable, null);

      IThrowableProxy throwableProxy = loggingEvent.getThrowableProxy();
      assertThat(throwableProxy).isNotNull();
      assertThat(throwableProxy.getStackTraceElementProxyArray())
          .isNotEmpty()
          .anySatisfy(PackagingDataCalculatorTest::assertApplicationFrameHasPackagingData);
    } finally {
      currentThread.setContextClassLoader(originalContextClassLoader);
      loggerContext.stop();
    }
  }

  private static Throwable createThrowableWithApplicationStackFrame() {
    return new IllegalStateException("packaging data failure");
  }

  private static void assertApplicationFrameHasPackagingData(StackTraceElementProxy stackTraceElementProxy) {
    assertThat(stackTraceElementProxy.getStackTraceElement().getClassName())
        .isEqualTo(PackagingDataCalculatorTest.class.getName());
    assertThat(stackTraceElementProxy.getClassPackagingData()).isNotNull();
  }
}
