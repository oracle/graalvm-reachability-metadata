/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class JavaSerializerTest {
    @Test
    void roundTripsSerializableObjectUsingJavaSerialization() {
        Kryo kryo = new Kryo();
        JavaSerializer serializer = new JavaSerializer();
        JavaSerializableMessage original = new JavaSerializableMessage(
                7,
                "kryo-java-serializer",
                new JavaSerializableDetails("details", 42L));

        JavaSerializableMessage read = roundTrip(kryo, serializer, original, JavaSerializableMessage.class);

        assertThat(read).isNotSameAs(original);
        assertThat(read.id).isEqualTo(original.id);
        assertThat(read.name).isEqualTo(original.name);
        assertThat(read.details.label).isEqualTo(original.details.label);
        assertThat(read.details.sequence).isEqualTo(original.details.sequence);
    }

    @Test
    void reusesJavaSerializationStreamAcrossConsecutiveObjects() {
        Kryo kryo = new Kryo();
        JavaSerializer serializer = new JavaSerializer();
        JavaSerializableDetails sharedDetails = new JavaSerializableDetails("shared", 100L);
        JavaSerializableMessage first = new JavaSerializableMessage(1, "first", sharedDetails);
        JavaSerializableMessage second = new JavaSerializableMessage(2, "second", sharedDetails);

        Output output = new Output(256, -1);
        serializer.write(kryo, output, first);
        serializer.write(kryo, output, second);
        kryo.reset();

        Input input = new Input(output.toBytes());
        JavaSerializableMessage firstRead = (JavaSerializableMessage) serializer.read(
                kryo, input, JavaSerializableMessage.class);
        JavaSerializableMessage secondRead = (JavaSerializableMessage) serializer.read(
                kryo, input, JavaSerializableMessage.class);
        kryo.reset();

        assertThat(firstRead.name).isEqualTo("first");
        assertThat(secondRead.name).isEqualTo("second");
        assertThat(firstRead.details).isSameAs(secondRead.details);
        assertThat(firstRead.details.label).isEqualTo(sharedDetails.label);
        assertThat(firstRead.details.sequence).isEqualTo(sharedDetails.sequence);
    }

    private static <T> T roundTrip(Kryo kryo, JavaSerializer serializer, T original, Class<T> type) {
        Output output = new Output(256, -1);
        serializer.write(kryo, output, original);
        kryo.reset();

        Input input = new Input(output.toBytes());
        T read = type.cast(serializer.read(kryo, input, type));
        kryo.reset();
        return read;
    }

    public static class JavaSerializableMessage implements Serializable {
        private static final long serialVersionUID = 1L;

        public int id;
        public String name;
        public JavaSerializableDetails details;

        public JavaSerializableMessage() {
        }

        public JavaSerializableMessage(int id, String name, JavaSerializableDetails details) {
            this.id = id;
            this.name = name;
            this.details = details;
        }
    }

    public static class JavaSerializableDetails implements Serializable {
        private static final long serialVersionUID = 1L;

        public String label;
        public long sequence;

        public JavaSerializableDetails() {
        }

        public JavaSerializableDetails(String label, long sequence) {
            this.label = label;
            this.sequence = sequence;
        }
    }
}
