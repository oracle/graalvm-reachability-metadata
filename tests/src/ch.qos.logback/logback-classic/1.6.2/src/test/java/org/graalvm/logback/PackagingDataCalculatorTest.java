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
  void calculatesPackagingDataForApplicationFrames() {
    LoggerContext loggerContext = new LoggerContext();
    try {
      loggerContext.setName("packaging-data-calculator-test");
      loggerContext.setPackagingDataEnabled(true);
      Logger logger = loggerContext.getLogger(PackagingDataCalculatorTest.class);
      Throwable throwable = createThrowableWithApplicationStackFrame();

      ILoggingEvent loggingEvent = new LoggingEvent(PackagingDataCalculatorTest.class.getName(), logger, Level.ERROR,
          "packaging data", throwable, null);

      IThrowableProxy throwableProxy = loggingEvent.getThrowableProxy();
      assertThat(throwableProxy).isNotNull();
      assertThat(throwableProxy.getStackTraceElementProxyArray())
          .isNotEmpty()
          .anySatisfy(PackagingDataCalculatorTest::assertApplicationFrameHasPackagingData);
    } finally {
      loggerContext.stop();
    }
  }

  private static Throwable createThrowableWithApplicationStackFrame() {
    Throwable throwable = new IllegalStateException("packaging data failure");
    throwable.setStackTrace(new StackTraceElement[] {
        new StackTraceElement(PackagingDataCalculatorTest.class.getName(), "testMethod", "Test.java", 1)
    });
    return throwable;
  }

  private static void assertApplicationFrameHasPackagingData(StackTraceElementProxy stackTraceElementProxy) {
    assertThat(stackTraceElementProxy.getStackTraceElement().getClassName())
        .isEqualTo(PackagingDataCalculatorTest.class.getName());
    assertThat(stackTraceElementProxy.getClassPackagingData()).isNotNull();
    assertThat(stackTraceElementProxy.getClassPackagingData().isExact()).isFalse();
  }
}
