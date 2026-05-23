/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class LoggingEventTest {

  @Test
  void resolvesUnnamedVirtualThreadNameThroughLoggerApi() throws Exception {
    LoggerContext loggerContext = new LoggerContext();
    VirtualThreadNameAppender appender = new VirtualThreadNameAppender();
    AtomicReference<Throwable> loggingFailure = new AtomicReference<>();
    try {
      loggerContext.setName("logging-event-virtual-thread-test");
      loggerContext.start();
      appender.setContext(loggerContext);
      appender.start();

      Logger logger = loggerContext.getLogger("virtual-thread-logging-event");
      logger.setAdditive(false);
      logger.setLevel(Level.INFO);
      logger.addAppender(appender);

      Thread virtualThread = Thread.ofVirtual().name("").start(() -> {
        try {
          logger.info("message from unnamed virtual thread");
        } catch (Throwable throwable) {
          loggingFailure.set(throwable);
        }
      });
      virtualThread.join(SECONDS.toMillis(10));

      assertThat(virtualThread.isAlive()).isFalse();
      assertThat(loggingFailure.get()).isNull();
      assertThat(appender.awaitThreadName()).startsWith(LoggingEvent.VIRTUAL_THREAD_NAME_PREFIX);
    } finally {
      appender.stop();
      loggerContext.stop();
    }
  }

  private static final class VirtualThreadNameAppender extends AppenderBase<ILoggingEvent> {

    private final CountDownLatch appended = new CountDownLatch(1);
    private final AtomicReference<String> threadName = new AtomicReference<>();

    @Override
    protected void append(ILoggingEvent eventObject) {
      threadName.set(eventObject.getThreadName());
      appended.countDown();
    }

    private String awaitThreadName() throws InterruptedException {
      assertThat(appended.await(10, SECONDS)).isTrue();
      return threadName.get();
    }
  }
}
