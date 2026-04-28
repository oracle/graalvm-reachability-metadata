/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class LoggingEventTest {

  @Test
  void derivesThreadNameForUnnamedCurrentThread() {
    Thread currentThread = Thread.currentThread();
    String originalName = currentThread.getName();

    try {
      currentThread.setName("");

      LoggingEvent event = new LoggingEvent();

      String threadName = event.getThreadName();

      assertThat(threadName.startsWith(LoggingEvent.REGULAR_UNNAMED_THREAD_PREFIX)
          || threadName.startsWith(LoggingEvent.VIRTUAL_THREAD_NAME_PREFIX)).isTrue();
    } finally {
      currentThread.setName(originalName);
    }
  }
}
