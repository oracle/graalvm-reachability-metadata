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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LevelTest {

    @Test
    void serializesAndDeserializesBuiltInLevelsToTheirStockInstances() throws IOException, ClassNotFoundException {
        Level restored = roundTrip(Level.WARN);

        assertThat(restored).isSameAs(Level.WARN);
        assertThat(restored.toInt()).isEqualTo(Level.WARN_INT);
        assertThat(restored.toString()).isEqualTo("WARN");
    }

    @Test
    void serializesAndDeserializesCustomLevelsAsSubclassInstances() throws IOException, ClassNotFoundException {
        Level restored = roundTrip(CustomLevel.CUSTOM);

        assertThat(restored)
                .isInstanceOf(CustomLevel.class)
                .isNotSameAs(CustomLevel.CUSTOM);
        assertThat(restored.toInt()).isEqualTo(CustomLevel.CUSTOM_INT);
        assertThat(restored.toString()).isEqualTo("CUSTOM");
    }

    private static Level roundTrip(Level level) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(level);
        }

        try (ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            return (Level) objectInput.readObject();
        }
    }

    public static final class CustomLevel extends Level {
        private static final long serialVersionUID = 1L;
        static final int CUSTOM_INT = 35000;
        static final CustomLevel CUSTOM = new CustomLevel();

        private CustomLevel() {
            super(CUSTOM_INT, "CUSTOM", 0);
        }
    }
}
