/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggingEventVOTest {

  @Test
  void preservesEventArgumentsDuringSerialization() throws Exception {
    LoggerContext context = new LoggerContext();
    try {
      Logger logger = context.getLogger(LoggingEventVOTest.class);
      LoggingEvent source = new LoggingEvent(LoggingEventVOTest.class.getName(), logger, Level.WARN,
          "request {} returned {}", null, new Object[] {"alpha", null});
      LoggingEventVO event = LoggingEventVO.build(source);

      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
        output.writeObject(event);
      }

      LoggingEventVO restored;
      try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
        restored = (LoggingEventVO) input.readObject();
      }

      assertThat(restored.getLevel()).isEqualTo(Level.WARN);
      assertThat(restored.getLoggerName()).isEqualTo(logger.getName());
      assertThat(restored.getArgumentArray()).containsExactly("alpha", null);
      assertThat(restored.getFormattedMessage()).isEqualTo("request alpha returned null");
    } finally {
      context.stop();
    }
  }
}
