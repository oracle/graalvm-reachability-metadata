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
    void serializesSerializableObjectThroughJavaObjectStreams() {
        Kryo writer = createKryo();
        JavaSerializableValue original = new JavaSerializableValue("alpha", 42);

        Output output = new Output(128, -1);
        writer.writeObject(output, original);
        output.close();

        Kryo reader = createKryo();
        JavaSerializableValue restored = reader.readObject(new Input(output.toBytes()), JavaSerializableValue.class);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.getCount()).isEqualTo(original.getCount());
    }

    private static Kryo createKryo() {
        Kryo kryo = new Kryo();
        kryo.register(JavaSerializableValue.class, new JavaSerializer());
        return kryo;
    }

    public static class JavaSerializableValue implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int count;

        JavaSerializableValue(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }
    }
}
