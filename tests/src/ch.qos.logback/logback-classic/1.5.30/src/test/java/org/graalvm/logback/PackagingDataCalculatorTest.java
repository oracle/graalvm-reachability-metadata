/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.util.concurrent.atomic.AtomicReference;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ClassPackagingData;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PackagingDataCalculatorTest {

  @Test
  void loggerCalculatesPackagingDataWhenContextClassLoaderCannotLoadApplicationFrames() {
    Thread currentThread = Thread.currentThread();
    ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    LoggerContext loggerContext = new LoggerContext();
    CapturingAppender appender = new CapturingAppender();
    try {
      loggerContext.setName("packaging-data-calculator-test");
      loggerContext.setPackagingDataEnabled(true);
      appender.setContext(loggerContext);
      appender.start();

      Logger logger = loggerContext.getLogger(PackagingDataCalculatorTest.class);
      logger.setLevel(Level.ERROR);
      logger.setAdditive(false);
      logger.addAppender(appender);

      currentThread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
      logger.error("packaging data", createThrowableWithApplicationStackFrame());

      ILoggingEvent loggingEvent = appender.getEvent();
      assertThat(loggingEvent).isNotNull();
      assertThat(loggingEvent.getFormattedMessage()).isEqualTo("packaging data");
      assertThat(loggingEvent.getThrowableProxy().getStackTraceElementProxyArray())
          .isNotEmpty()
          .anySatisfy(PackagingDataCalculatorTest::assertApplicationFrameHasPackagingData);
    } finally {
      currentThread.setContextClassLoader(originalContextClassLoader);
      appender.stop();
      loggerContext.stop();
    }
  }

  private static Throwable createThrowableWithApplicationStackFrame() {
    return new IllegalStateException("packaging data failure");
  }

  private static void assertApplicationFrameHasPackagingData(StackTraceElementProxy stackTraceElementProxy) {
    assertThat(stackTraceElementProxy.getStackTraceElement().getClassName())
        .isEqualTo(PackagingDataCalculatorTest.class.getName());
    ClassPackagingData packagingData = stackTraceElementProxy.getClassPackagingData();
    assertThat(packagingData).isNotNull();
    assertThat(packagingData.getCodeLocation()).isNotBlank();
  }

  private static final class CapturingAppender extends AppenderBase<ILoggingEvent> {

    private final AtomicReference<ILoggingEvent> event = new AtomicReference<>();

    @Override
    protected void append(ILoggingEvent loggingEvent) {
      event.set(loggingEvent);
    }

    private ILoggingEvent getEvent() {
      return event.get();
    }
  }
}
