/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time.joda_time;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.joda.time.LocalTime;
import org.junit.jupiter.api.Test;

public class LocalTimeInnerPropertyTest {

    @Test
    void serializesAndDeserializesSecondOfMinuteProperty() throws Exception {
        LocalTime.Property original = new LocalTime(12, 34, 56, 789).secondOfMinute();

        LocalTime.Property restored = deserialize(serialize(original));

        assertThat(restored.get()).isEqualTo(original.get());
        assertThat(restored.getFieldType()).isEqualTo(original.getFieldType());
        assertThat(restored.getLocalTime()).isEqualTo(original.getLocalTime());
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
        }
        return output.toByteArray();
    }

    private static LocalTime.Property deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (LocalTime.Property) objectInput.readObject();
        }
    }
}
