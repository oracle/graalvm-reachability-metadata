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
import ch.qos.logback.classic.spi.ClassPackagingData;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PackagingDataCalculatorTest {

  @Test
  void shouldCalculatePackagingDataWithContextClassLoader() {
    ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(PackagingDataCalculatorTest.class.getClassLoader());
    try {
      ILoggingEvent event = logExceptionWithPackagingData(new IllegalStateException("context classloader"));

      assertThat(event.getLevel()).isEqualTo(Level.ERROR);
      assertThat(packagingDataForThisTest(event))
          .isNotNull()
          .extracting(ClassPackagingData::isExact)
          .isEqualTo(false);
    } finally {
      Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }
  }

  @Test
  void shouldCalculatePackagingDataWithoutContextClassLoader() {
    ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(null);
    try {
      ILoggingEvent event = logExceptionWithPackagingData(new IllegalArgumentException("class for name"));

      assertThat(event.getLevel()).isEqualTo(Level.ERROR);
      assertThat(packagingDataForThisTest(event))
          .isNotNull()
          .extracting(ClassPackagingData::isExact)
          .isEqualTo(false);
    } finally {
      Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }
  }

  private ILoggingEvent logExceptionWithPackagingData(RuntimeException exception) {
    LoggerContext context = new LoggerContext();
    context.setName("packaging-data-calculator-test");
    context.setPackagingDataEnabled(true);

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.setContext(context);
    appender.start();

    Logger logger = context.getLogger(PackagingDataCalculatorTest.class);
    logger.addAppender(appender);
    logger.setAdditive(false);
    try {
      logger.error("exercise packaging data", exception);
      assertThat(appender.list).hasSize(1);
      ILoggingEvent event = appender.list.get(0);
      assertThat(event.getThrowableProxy()).isNotNull();
      return event;
    } finally {
      logger.detachAppender(appender);
      appender.stop();
      context.stop();
    }
  }

  private ClassPackagingData packagingDataForThisTest(ILoggingEvent event) {
    IThrowableProxy throwableProxy = event.getThrowableProxy();
    assertThat(throwableProxy.getClassName()).isIn(
        IllegalStateException.class.getName(), IllegalArgumentException.class.getName());

    for (StackTraceElementProxy step : throwableProxy.getStackTraceElementProxyArray()) {
      if (PackagingDataCalculatorTest.class.getName().equals(step.getStackTraceElement().getClassName())) {
        return step.getClassPackagingData();
      }
    }
    return null;
  }
}
