/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.pattern.LogEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogEventTest {

    @Test
    void serializesAndDeserializesBuiltInLevels() throws IOException, ClassNotFoundException {
        LogEvent original = new LogEvent(
                LogEventTest.class.getName(),
                Logger.getLogger(LogEventTest.class),
                Level.DEBUG,
                "built-in level",
                null);

        LogEvent restored = roundTrip(original);

        assertThat(restored.getLevel()).isSameAs(Level.DEBUG);
        assertThat(restored.getRenderedMessage()).isEqualTo("built-in level");
        assertThat(restored.getLoggerName()).isEqualTo(LogEventTest.class.getName());
    }

    @Test
    void serializesAndDeserializesCustomLevelsUsingTheSubclassFactoryMethod() throws IOException, ClassNotFoundException {
        LogEvent original = new LogEvent(
                LogEventTest.class.getName(),
                Logger.getLogger("custom-level-logger"),
                CustomLevel.CUSTOM,
                "custom level",
                null);

        LogEvent restored = roundTrip(original);

        assertThat(restored.getLevel()).isSameAs(CustomLevel.CUSTOM);
        assertThat(restored.getLevel().toInt()).isEqualTo(CustomLevel.CUSTOM_INT);
        assertThat(restored.getRenderedMessage()).isEqualTo("custom level");
    }

    private static LogEvent roundTrip(LogEvent event) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(event);
        }

        try (ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            return (LogEvent) objectInput.readObject();
        }
    }

    public static final class CustomLevel extends Level {
        private static final long serialVersionUID = 1L;
        static final int CUSTOM_INT = 35000;
        static final CustomLevel CUSTOM = new CustomLevel();

        private CustomLevel() {
            super(CUSTOM_INT, "CUSTOM", 0);
        }

        public static Level toLevel(int value) {
            if (value == CUSTOM_INT) {
                return CUSTOM;
            }
            return Level.toLevel(value);
        }
    }
}
