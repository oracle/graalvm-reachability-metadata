/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.helpers.UtilLoggingLevel;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

public class LoggingEventTest {
    private static final Logger LOGGER = Logger.getLogger(LoggingEventTest.class);
    private static final String MESSAGE = "serialized logging event message";

    @Test
    void roundTripsBuiltInLevelThroughLoggingEventSerialization() throws Exception {
        LoggingEvent event = new LoggingEvent(LoggingEventTest.class.getName(), LOGGER, Level.INFO, MESSAGE, null);

        LoggingEvent restored = deserialize(serialize(event));

        assertThat(restored.getLevel()).isSameAs(Level.INFO);
        assertThat(restored.getLoggerName()).isEqualTo(LOGGER.getName());
        assertThat(restored.getMessage()).isEqualTo(MESSAGE);
    }

    @Test
    void roundTripsLevelSubclassThroughDeclaredToLevelFactory() throws Exception {
        LoggingEvent event = new LoggingEvent(LoggingEventTest.class.getName(), LOGGER, UtilLoggingLevel.FINE, MESSAGE,
                null);

        LoggingEvent restored = deserialize(serialize(event));

        assertThat(restored.getLevel()).isSameAs(UtilLoggingLevel.FINE);
        assertThat(restored.getLoggerName()).isEqualTo(LOGGER.getName());
        assertThat(restored.getMessage()).isEqualTo(MESSAGE);
    }

    private static byte[] serialize(LoggingEvent event) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(event);
        }
        return output.toByteArray();
    }

    private static LoggingEvent deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInput.readObject();
            assertThat(restored).isInstanceOf(LoggingEvent.class);
            return (LoggingEvent) restored;
        }
    }
}
