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
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class LoggingEventVOTest {

  @Test
  void serializesAndDeserializesArgumentArrayElements() throws Exception {
    LoggingEventVO original = createLoggingEventVO("alpha", null, 42);

    LoggingEventVO restored = deserialize(serialize(original));

    assertThat(restored.getLevel()).isEqualTo(Level.INFO);
    assertThat(restored.getMessage()).isEqualTo("Processed {} {} {}");
    assertThat(restored.getArgumentArray()).containsExactly("alpha", null, "42");
    assertThat(restored.getFormattedMessage()).isEqualTo("Processed alpha null 42");
  }

  private static LoggingEventVO createLoggingEventVO(Object... arguments) {
    LoggerContext context = new LoggerContext();
    context.setName("logging-event-vo-test");
    context.setMDCAdapter(new LogbackMDCAdapter());

    Logger logger = context.getLogger(LoggingEventVOTest.class);
    LoggingEvent event = new LoggingEvent(
        LoggingEventVOTest.class.getName(), logger, Level.INFO, "Processed {} {} {}", null, arguments);
    event.prepareForDeferredProcessing();
    return LoggingEventVO.build(event);
  }

  private static byte[] serialize(LoggingEventVO value) throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
      objectOutputStream.writeObject(value);
    }

    return outputStream.toByteArray();
  }

  private static LoggingEventVO deserialize(byte[] serialized) throws Exception {
    try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      return (LoggingEventVO) objectInputStream.readObject();
    }
  }
}
