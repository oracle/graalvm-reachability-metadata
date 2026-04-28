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
  void derivesFallbackNameForUnnamedCurrentThread() {
    Thread currentThread = Thread.currentThread();
    String originalThreadName = currentThread.getName();

    try {
      currentThread.setName("");

      LoggingEvent event = new LoggingEvent();
      String threadName = event.getThreadName();

      assertThat(threadName).isIn(
          LoggingEvent.REGULAR_UNNAMED_THREAD_PREFIX + currentThread.getId(),
          LoggingEvent.VIRTUAL_THREAD_NAME_PREFIX + currentThread.getId());
    } finally {
      currentThread.setName(originalThreadName);
    }
  }
}
