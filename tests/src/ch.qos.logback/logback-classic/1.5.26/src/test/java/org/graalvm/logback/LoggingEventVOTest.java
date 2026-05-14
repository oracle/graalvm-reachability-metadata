/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggingEventVOTest {

  @Test
  void roundTripsArgumentArrayThroughJavaSerialization() throws Exception {
    LoggingEventVO event = createEventWithArguments("serialized {} {}", new Object[] {"value", null});

    byte[] serialized = serialize(event);
    LoggingEventVO deserialized = deserialize(serialized);

    assertThat(deserialized).isNotSameAs(event);
    assertThat(deserialized.getLoggerName()).isEqualTo(event.getLoggerName());
    assertThat(deserialized.getLevel()).isEqualTo(Level.INFO);
    assertThat(deserialized.getMessage()).isEqualTo("serialized {} {}");
    assertThat(deserialized.getArgumentArray()).containsExactly("value", null);
    assertThat(deserialized.getFormattedMessage()).isEqualTo("serialized value null");
  }

  private static LoggingEventVO createEventWithArguments(String message, Object[] arguments) {
    LoggerContext loggerContext = new LoggerContext();
    try {
      loggerContext.setName("logging-event-vo-test");
      loggerContext.setMDCAdapter(new LogbackMDCAdapter());
      Logger logger = loggerContext.getLogger(LoggingEventVOTest.class);
      LoggingEvent event = new LoggingEvent(LoggingEventVOTest.class.getName(), logger, Level.INFO, message, null,
          arguments);
      return LoggingEventVO.build(event);
    } finally {
      loggerContext.stop();
    }
  }

  private static byte[] serialize(LoggingEventVO event) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
      outputStream.writeObject(event);
    }
    return bytes.toByteArray();
  }

  private static LoggingEventVO deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
    try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      Object object = inputStream.readObject();
      assertThat(object).isInstanceOf(LoggingEventVO.class);
      return (LoggingEventVO) object;
    }
  }
}
