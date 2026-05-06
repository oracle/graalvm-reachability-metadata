/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.pattern.LogEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogEventTest {
    private static final String LOGGER_NAME = "reload4j.log-event.serialization";

    @Test
    void serializesAndDeserializesBuiltInLevel() throws Exception {
        LogEvent event = new LogEvent(
                LogEventTest.class.getName(),
                Logger.getLogger(LOGGER_NAME),
                Level.INFO,
                "built-in level message",
                null);

        LogEvent deserialized = serializeAndDeserialize(event);

        assertThat(deserialized.getLoggerName()).isEqualTo(LOGGER_NAME);
        assertThat(deserialized.getLevel()).isSameAs(Level.INFO);
        assertThat(deserialized.getMessage()).isEqualTo("built-in level message");
    }

    @Test
    void serializesAndDeserializesCustomLevelThroughLevelFactoryMethod() throws Exception {
        CustomSerializableLevel.toLevelCalls = 0;
        LogEvent event = new LogEvent(
                LogEventTest.class.getName(),
                Logger.getLogger(LOGGER_NAME),
                CustomSerializableLevel.AUDIT,
                "custom level message",
                null);

        LogEvent deserialized = serializeAndDeserialize(event);

        assertThat(deserialized.getLevel()).isSameAs(CustomSerializableLevel.AUDIT);
        assertThat(deserialized.getLevel().toInt()).isEqualTo(CustomSerializableLevel.AUDIT_INT);
        assertThat(deserialized.getMessage()).isEqualTo("custom level message");
        assertThat(CustomSerializableLevel.toLevelCalls).isEqualTo(1);
    }

    private static LogEvent serializeAndDeserialize(LogEvent event) throws Exception {
        byte[] serialized = serialize(event);
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object deserialized = inputStream.readObject();
            assertThat(deserialized).isInstanceOf(LogEvent.class);
            return (LogEvent) deserialized;
        }
    }

    private static byte[] serialize(LogEvent event) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(event);
        }
        return outputStream.toByteArray();
    }

    public static final class CustomSerializableLevel extends Level {
        private static final int AUDIT_INT = 55000;
        private static final Level AUDIT = new CustomSerializableLevel(AUDIT_INT, "AUDIT", 0);
        private static int toLevelCalls;

        private CustomSerializableLevel(int level, String levelStr, int syslogEquivalent) {
            super(level, levelStr, syslogEquivalent);
        }

        public static Level toLevel(int value) {
            toLevelCalls++;
            if (value == AUDIT_INT) {
                return AUDIT;
            }
            return Level.toLevel(value);
        }
    }
}
