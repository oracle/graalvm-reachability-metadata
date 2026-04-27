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
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LoggingEventTest {
    private static final Logger LOGGER = Logger.getLogger(LoggingEventTest.class);
    private static final String LOGGER_FQCN = LoggingEventTest.class.getName();
    private static final int CUSTOM_LEVEL_VALUE = 45_000;
    private static final CustomLevel CUSTOM_LEVEL = new CustomLevel(CUSTOM_LEVEL_VALUE, "CUSTOM_EVENT_LEVEL", 7);

    @BeforeEach
    void resetCustomLevelLookup() {
        CustomLevel.lastRequestedValue = null;
    }

    @Test
    void serializesAndDeserializesStandardLoggingEventLevel() throws Exception {
        LoggingEvent event = new LoggingEvent(LOGGER_FQCN, LOGGER, Level.INFO, "standard-logging-event-message", null);

        LoggingEvent deserializedEvent = roundTrip(event);

        assertThat(deserializedEvent.getLoggerName()).isEqualTo(LOGGER.getName());
        assertThat(deserializedEvent.getLevel()).isSameAs(Level.INFO);
        assertThat(deserializedEvent.getMessage()).isEqualTo("standard-logging-event-message");
    }

    @Test
    void serializesAndDeserializesCustomLoggingEventLevel() throws Exception {
        LoggingEvent event = new LoggingEvent(LOGGER_FQCN, LOGGER, CUSTOM_LEVEL, "custom-logging-event-message", null);

        LoggingEvent deserializedEvent = roundTrip(event);

        assertThat(deserializedEvent.getLoggerName()).isEqualTo(LOGGER.getName());
        assertThat(deserializedEvent.getLevel()).isSameAs(CUSTOM_LEVEL);
        assertThat(deserializedEvent.getMessage()).isEqualTo("custom-logging-event-message");
        assertThat(CustomLevel.lastRequestedValue).isEqualTo(CUSTOM_LEVEL_VALUE);
    }

    private static LoggingEvent roundTrip(LoggingEvent event) throws IOException, ClassNotFoundException {
        byte[] serializedEvent = serialize(event);
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializedEvent))) {
            return (LoggingEvent) input.readObject();
        }
    }

    private static byte[] serialize(LoggingEvent event) throws IOException {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(outputBytes)) {
            output.writeObject(event);
        }
        return outputBytes.toByteArray();
    }

    public static final class CustomLevel extends Level {
        private static Integer lastRequestedValue;

        private CustomLevel(int level, String levelStr, int syslogEquivalent) {
            super(level, levelStr, syslogEquivalent);
        }

        public static Level toLevel(int level) {
            lastRequestedValue = level;
            return CUSTOM_LEVEL;
        }
    }
}
