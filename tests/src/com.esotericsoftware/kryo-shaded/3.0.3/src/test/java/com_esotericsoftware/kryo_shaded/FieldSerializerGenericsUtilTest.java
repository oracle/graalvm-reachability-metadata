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
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.esotericsoftware.kryo.util.UnsafeUtil;
import com.esotericsoftware.kryo.util.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FieldSerializerGenericsUtilTest {
    static {
        initializeFieldSerializerUnsafeDetection();
    }

    @BeforeEach
    void disableRuntimeCodeGeneration() {
        initializeFieldSerializerUnsafeDetection();
        Util.isAndroid = true;
    }

    private static void initializeFieldSerializerUnsafeDetection() {
        Util.isAndroid = false;
        UnsafeUtil.unsafe();
        try {
            Class.forName(FieldSerializer.class.getName());
        } catch (ClassNotFoundException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Test
    void serializesParameterizedFieldWhoseTypeArgumentIsGenericArray() {
        Kryo kryo = newKryo();
        GenericArrayRoot original = new GenericArrayRoot(
                new GenericArrayEnvelope<>(new GenericArrayHolder<>(new String[] {"alpha", "beta"})));

        Output output = new Output(256, -1);
        kryo.writeObject(output, original);
        output.close();

        Input input = new Input(output.toBytes());
        GenericArrayRoot restored = kryo.readObject(input, GenericArrayRoot.class);
        input.close();

        assertThat(restored.envelope.holder.value).containsExactly("alpha", "beta");
    }

    private static Kryo newKryo() {
        Kryo kryo = new Kryo();
        kryo.setAsmEnabled(false);
        kryo.setReferences(false);
        registerFieldSerializer(kryo, GenericArrayRoot.class);
        registerFieldSerializer(kryo, GenericArrayEnvelope.class);
        registerFieldSerializer(kryo, GenericArrayHolder.class);
        kryo.register(String[].class);
        return kryo;
    }

    private static <T> void registerFieldSerializer(Kryo kryo, Class<T> type) {
        FieldSerializer<T> serializer = new FieldSerializer<>(kryo, type);
        kryo.register(type, serializer);
    }

    public static class GenericArrayRoot {
        public GenericArrayEnvelope<String> envelope;

        public GenericArrayRoot() {
        }

        GenericArrayRoot(GenericArrayEnvelope<String> envelope) {
            this.envelope = envelope;
        }
    }

    public static class GenericArrayEnvelope<T> {
        public GenericArrayHolder<T[]> holder;

        public GenericArrayEnvelope() {
        }

        GenericArrayEnvelope(GenericArrayHolder<T[]> holder) {
            this.holder = holder;
        }
    }

    public static class GenericArrayHolder<T> {
        public T value;

        public GenericArrayHolder() {
        }

        GenericArrayHolder(T value) {
            this.value = value;
        }
    }
}
