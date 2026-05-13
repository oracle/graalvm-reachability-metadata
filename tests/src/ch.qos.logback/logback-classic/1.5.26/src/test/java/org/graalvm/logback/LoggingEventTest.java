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
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggingEventTest {

  @Test
  void shouldResolveNameForUnnamedThread() throws Exception {
    LoggerContext context = new LoggerContext();
    context.setName("logging-event-test");

    try {
      Logger logger = context.getLogger(LoggingEventTest.class);
      LoggingEvent event = new LoggingEvent(Logger.class.getName(), logger, Level.INFO, "unnamed thread", null, null);
      AtomicReference<String> extractedThreadName = new AtomicReference<>();
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread unnamedThread = new Thread(() -> {
        try {
          extractedThreadName.set(event.getThreadName());
        } catch (Throwable ex) {
          failure.set(ex);
        }
      }, "");

      unnamedThread.start();
      unnamedThread.join(5_000);

      assertThat(unnamedThread.isAlive()).isFalse();
      assertThat(failure.get()).isNull();
      assertThat(extractedThreadName.get())
          .startsWith(LoggingEvent.REGULAR_UNNAMED_THREAD_PREFIX)
          .matches(LoggingEvent.REGULAR_UNNAMED_THREAD_PREFIX + "\\d+");
    } finally {
      context.stop();
    }
  }
}
