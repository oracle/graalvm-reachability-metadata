/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_classic;

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
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggingEventVOTest {

    @Test
    void serializableEventRetainsArgumentsAndContext() throws Exception {
        LoggerContext context = new LoggerContext();
        context.setName("producer-context");
        context.setMDCAdapter(new LogbackMDCAdapter());
        try {
            Logger logger = context.getLogger("orders");
            LoggingEvent event = new LoggingEvent(Logger.class.getName(), logger, Level.WARN,
                    "order {} has note {}", null, new Object[]{42, null});
            event.setMDCPropertyMap(Map.of("request", "request-a"));
            LoggingEventVO original = LoggingEventVO.build(event);

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
                output.writeObject(original);
            }
            try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                LoggingEventVO received = (LoggingEventVO) input.readObject();
                assertThat(received.getLoggerName()).isEqualTo("orders");
                assertThat(received.getLoggerContextVO().getName()).isEqualTo("producer-context");
                assertThat(received.getLevel()).isEqualTo(Level.WARN);
                assertThat(received.getArgumentArray()).containsExactly("42", null);
                assertThat(received.getFormattedMessage()).isEqualTo("order 42 has note null");
                assertThat(received.getMDCPropertyMap()).containsEntry("request", "request-a");
                assertThat(received.getTimeStamp()).isEqualTo(original.getTimeStamp());
                assertThat(received.getThreadName()).isEqualTo(original.getThreadName());
            }
        } finally {
            context.stop();
        }
    }
}
