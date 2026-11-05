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
import java.util.HashMap;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggingEventVOTest {

  @Test
  void preservesEventAndNullableArgumentsAcrossSerialization() throws Exception {
    LoggerContext context = new LoggerContext();
    try {
      context.setName("serialized-context");
      LoggingEvent event = new LoggingEvent(LoggingEventVOTest.class.getName(),
          context.getLogger("remote.orders"), Level.WARN, "order {} has owner {}", null,
          new Object[] {42, null});
      HashMap<String, String> mdc = new HashMap<>();
      mdc.put("request", "order-request");
      event.setMDCPropertyMap(mdc);
      event.setThreadName("order-worker");
      event.setTimeStamp(123456789L);
      LoggingEventVO original = LoggingEventVO.build(event);

      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
        output.writeObject(original);
      }
      try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
        LoggingEventVO restored = (LoggingEventVO) input.readObject();
        assertThat(restored).isEqualTo(original).isNotSameAs(original);
        assertThat(restored.getLoggerName()).isEqualTo("remote.orders");
        assertThat(restored.getLoggerContextVO().getName()).isEqualTo("serialized-context");
        assertThat(restored.getLevel()).isEqualTo(Level.WARN);
        assertThat(restored.getMessage()).isEqualTo("order {} has owner {}");
        assertThat(restored.getArgumentArray()).containsExactly("42", null);
        assertThat(restored.getFormattedMessage()).isEqualTo("order 42 has owner null");
        assertThat(restored.getMDCPropertyMap()).containsEntry("request", "order-request");
        assertThat(restored.getThreadName()).isEqualTo("order-worker");
        assertThat(restored.getTimeStamp()).isEqualTo(123456789L);
      }
    } finally {
      context.stop();
    }
  }
}
