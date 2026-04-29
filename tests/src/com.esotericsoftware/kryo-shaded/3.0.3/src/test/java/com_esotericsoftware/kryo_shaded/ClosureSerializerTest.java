/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ClosureSerializer;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class ClosureSerializerTest {
    @Test
    void serializesReadsAndCopiesSerializableLambdaClosures() {
        ClosureSerializer serializer = new ClosureSerializer();
        RecordingKryo kryo = new RecordingKryo();
        String prefix = "kryo:";
        SerializableTransformer transformer = value -> prefix + value.toUpperCase(Locale.ROOT);

        serializer.write(kryo, new Output(128, -1), transformer);

        assertThat(kryo.closureRepresentation).isInstanceOf(SerializedLambda.class);

        SerializableTransformer restored = (SerializableTransformer) serializer.read(
                kryo,
                new Input(new byte[0]),
                transformer.getClass());
        SerializableTransformer copied = (SerializableTransformer) serializer.copy(kryo, transformer);

        assertThat(restored.transform("closure")).isEqualTo("kryo:CLOSURE");
        assertThat(copied.transform("serializer")).isEqualTo("kryo:SERIALIZER");
    }

    @FunctionalInterface
    private interface SerializableTransformer extends Serializable {
        String transform(String value);
    }

    private static final class RecordingKryo extends Kryo {
        private Object closureRepresentation;

        @Override
        public void writeObject(Output output, Object object) {
            closureRepresentation = object;
        }

        @Override
        public <T> T readObject(Input input, Class<T> type) {
            return type.cast(closureRepresentation);
        }
    }
}
