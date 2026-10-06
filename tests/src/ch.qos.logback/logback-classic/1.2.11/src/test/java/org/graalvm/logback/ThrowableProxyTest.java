/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.io.IOException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ThrowableProxyTest {

  @Test
  void capturesSuppressedExceptionsFromALoggingEvent() {
    LoggerContext context = new LoggerContext();
    try {
      Logger logger = context.getLogger(ThrowableProxyTest.class);
      IllegalStateException failure = new IllegalStateException("request failed");
      failure.addSuppressed(new IOException("cleanup failed"));

      LoggingEvent event = new LoggingEvent(ThrowableProxyTest.class.getName(), logger, Level.ERROR,
          "operation failed", failure, null);
      IThrowableProxy proxy = event.getThrowableProxy();

      assertThat(proxy).isNotNull();
      assertThat(proxy.getClassName()).isEqualTo(IllegalStateException.class.getName());
      assertThat(proxy.getSuppressed()).singleElement().satisfies(suppressed -> {
        assertThat(suppressed.getClassName()).isEqualTo(IOException.class.getName());
        assertThat(suppressed.getMessage()).isEqualTo("cleanup failed");
      });
    } finally {
      context.stop();
    }
  }
}
