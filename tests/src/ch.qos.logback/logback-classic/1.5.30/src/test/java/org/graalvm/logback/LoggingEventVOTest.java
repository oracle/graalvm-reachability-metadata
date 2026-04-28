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
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggingEventVOTest {

  @Test
  void serializesAndDeserializesArgumentArrayElements() throws Exception {
    LoggingEventVO event = LoggingEventVO.build(createLoggingEvent());

    byte[] serializedEvent = serialize(event);
    LoggingEventVO restoredEvent = deserialize(serializedEvent);

    assertThat(restoredEvent.getLevel()).isEqualTo(Level.INFO);
    assertThat(restoredEvent.getMessage()).isEqualTo("event with {} and {}");
    assertThat(restoredEvent.getArgumentArray()).containsExactly("structured-value", null);
    assertThat(restoredEvent.getFormattedMessage()).isEqualTo("event with structured-value and null");
    assertThat(restoredEvent.getMDCPropertyMap()).containsEntry("requestId", "native-image");
  }

  private LoggingEvent createLoggingEvent() {
    LoggerContext loggerContext = new LoggerContext();
    loggerContext.setName("serialization-context");
    Logger logger = loggerContext.getLogger("serialization.logger");
    LoggingEvent event = new LoggingEvent(
        LoggingEventVOTest.class.getName(),
        logger,
        Level.INFO,
        "event with {} and {}",
        null,
        new Object[] {new StructuredArgument("structured-value"), null});
    event.setThreadName("serialization-thread");
    event.setMDCPropertyMap(Map.of("requestId", "native-image"));
    return event;
  }

  private byte[] serialize(LoggingEventVO event) throws Exception {
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    try (ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
      objectStream.writeObject(event);
    }
    return byteStream.toByteArray();
  }

  private LoggingEventVO deserialize(byte[] serializedEvent) throws Exception {
    try (ObjectInputStream objectStream = new ObjectInputStream(new ByteArrayInputStream(serializedEvent))) {
      return (LoggingEventVO) objectStream.readObject();
    }
  }

  private static final class StructuredArgument {

    private final String value;

    private StructuredArgument(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }
  }
}
