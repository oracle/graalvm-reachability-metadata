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
  void shouldSerializeAndDeserializeArgumentArrayEntries() throws Exception {
    LoggerContext context = new LoggerContext();
    context.setName("logging-event-vo-test");
    context.setMDCAdapter(new LogbackMDCAdapter());

    try {
      Logger logger = context.getLogger("org.graalvm.logback.LoggingEventVOTest");
      LoggingEvent event = new LoggingEvent(Logger.class.getName(), logger, Level.INFO, "values {} {}",
          null, new Object[] { 42, null });
      LoggingEventVO eventVO = LoggingEventVO.build(event);

      LoggingEventVO deserializedEventVO = deserialize(serialize(eventVO));

      assertThat(deserializedEventVO.getLoggerName()).isEqualTo(logger.getName());
      assertThat(deserializedEventVO.getLevel()).isEqualTo(Level.INFO);
      assertThat(deserializedEventVO.getArgumentArray()).containsExactly("42", null);
      assertThat(deserializedEventVO.getFormattedMessage()).isEqualTo("values 42 null");
    } finally {
      context.stop();
    }
  }

  private byte[] serialize(LoggingEventVO eventVO) throws Exception {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
      objectOutputStream.writeObject(eventVO);
    }
    return byteArrayOutputStream.toByteArray();
  }

  private LoggingEventVO deserialize(byte[] serializedEventVO) throws Exception {
    try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serializedEventVO))) {
      return (LoggingEventVO) objectInputStream.readObject();
    }
  }
}
