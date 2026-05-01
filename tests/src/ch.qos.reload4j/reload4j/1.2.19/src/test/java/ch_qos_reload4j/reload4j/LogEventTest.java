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
import org.apache.log4j.pattern.LogEvent;
import org.junit.jupiter.api.Test;

public class LogEventTest {
    private static final Logger LOGGER = Logger.getLogger(LogEventTest.class);
    private static final String MESSAGE = "serialized log event message";

    @Test
    void roundTripsBuiltInLevelThroughLogEventSerialization() throws Exception {
        LogEvent event = new LogEvent(LogEventTest.class.getName(), LOGGER, Level.INFO, MESSAGE, null);

        LogEvent restored = deserialize(serialize(event));

        assertThat(restored.getLevel()).isSameAs(Level.INFO);
        assertThat(restored.getLoggerName()).isEqualTo(LOGGER.getName());
        assertThat(restored.getMessage()).isEqualTo(MESSAGE);
    }

    @Test
    void roundTripsCustomLevelThroughDeclaredToLevelFactory() throws Exception {
        SerializableLevel.lastRequestedLevel = 0;
        LogEvent event = new LogEvent(LogEventTest.class.getName(), LOGGER, SerializableLevel.CUSTOM, MESSAGE, null);

        LogEvent restored = deserialize(serialize(event));

        assertThat(restored.getLevel()).isSameAs(SerializableLevel.CUSTOM);
        assertThat(SerializableLevel.lastRequestedLevel).isEqualTo(SerializableLevel.CUSTOM.toInt());
        assertThat(restored.getLoggerName()).isEqualTo(LOGGER.getName());
        assertThat(restored.getMessage()).isEqualTo(MESSAGE);
    }

    private static byte[] serialize(LogEvent event) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(event);
        }
        return output.toByteArray();
    }

    private static LogEvent deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInput.readObject();
            assertThat(restored).isInstanceOf(LogEvent.class);
            return (LogEvent) restored;
        }
    }

    public static final class SerializableLevel extends Level {
        public static final SerializableLevel CUSTOM = new SerializableLevel(35000, "CUSTOM", 4);
        static int lastRequestedLevel;

        private SerializableLevel(int level, String levelStr, int syslogEquivalent) {
            super(level, levelStr, syslogEquivalent);
        }

        public static Level toLevel(int level) {
            lastRequestedLevel = level;
            if (CUSTOM.toInt() == level) {
                return CUSTOM;
            }
            return Level.toLevel(level);
        }
    }
}
