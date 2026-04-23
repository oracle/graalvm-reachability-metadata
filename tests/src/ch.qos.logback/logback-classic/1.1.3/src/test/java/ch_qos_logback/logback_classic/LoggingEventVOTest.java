/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_classic;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class LoggingEventVOTest {

    @Test
    void serializesAndDeserializesArgumentArraysIncludingNullElements() throws Exception {
        LoggerContext context = new LoggerContext();
        try {
            Logger logger = context.getLogger("logging-event-vo");
            LoggingEvent event = new LoggingEvent(
                    getClass().getName(),
                    logger,
                    Level.WARN,
                    "payload {} {}",
                    null,
                    new Object[] {"first", null }
            );
            LoggingEventVO original = LoggingEventVO.build(event);

            LoggingEventVO restored;
            try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                 ObjectOutputStream outputStream = new ObjectOutputStream(byteStream)) {
                outputStream.writeObject(original);
                outputStream.flush();

                try (ObjectInputStream inputStream = new ObjectInputStream(
                        new ByteArrayInputStream(byteStream.toByteArray()))) {
                    restored = (LoggingEventVO) inputStream.readObject();
                }
            }

            assertThat(restored.getLoggerName()).isEqualTo(logger.getName());
            assertThat(restored.getLevel()).isEqualTo(Level.WARN);
            assertThat(restored.getArgumentArray()).containsExactly("first", null);
            assertThat(restored.getFormattedMessage()).isEqualTo("payload first null");
        } finally {
            context.stop();
        }
    }
}
