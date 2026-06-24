/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ReflectionSerializerFactoryTest {
    @BeforeEach
    void disableRuntimeCodeGeneration() {
        Util.isAndroid = true;
    }

    @Test
    void createsSerializerUsingKryoAndTypeConstructor() {
        Kryo kryo = newKryo();

        KryoAndTypePayloadSerializer serializer = registerAndRoundTrip(kryo, KryoAndTypePayloadSerializer.class);

        assertThat(serializer.constructorName).isEqualTo("kryo-and-type");
        assertThat(serializer.constructedWithKryo).isSameAs(kryo);
        assertThat(serializer.constructedForType).isSameAs(Payload.class);
    }

    @Test
    void createsSerializerUsingKryoConstructor() {
        Kryo kryo = newKryo();

        KryoOnlyPayloadSerializer serializer = registerAndRoundTrip(kryo, KryoOnlyPayloadSerializer.class);

        assertThat(serializer.constructorName).isEqualTo("kryo-only");
        assertThat(serializer.constructedWithKryo).isSameAs(kryo);
        assertThat(serializer.constructedForType).isNull();
    }

    @Test
    void createsSerializerUsingTypeConstructor() {
        Kryo kryo = newKryo();

        TypeOnlyPayloadSerializer serializer = registerAndRoundTrip(kryo, TypeOnlyPayloadSerializer.class);

        assertThat(serializer.constructorName).isEqualTo("type-only");
        assertThat(serializer.constructedWithKryo).isNull();
        assertThat(serializer.constructedForType).isSameAs(Payload.class);
    }

    @Test
    void createsSerializerUsingNoArgumentConstructor() {
        Kryo kryo = newKryo();

        NoArgumentPayloadSerializer serializer = registerAndRoundTrip(kryo, NoArgumentPayloadSerializer.class);

        assertThat(serializer.constructorName).isEqualTo("no-argument");
        assertThat(serializer.constructedWithKryo).isNull();
        assertThat(serializer.constructedForType).isNull();
    }

    private static Kryo newKryo() {
        Kryo kryo = new Kryo();
        kryo.setAsmEnabled(false);
        kryo.setReferences(false);
        return kryo;
    }

    private static <T extends RecordingPayloadSerializer> T registerAndRoundTrip(Kryo kryo, Class<T> serializerClass) {
        kryo.addDefaultSerializer(Payload.class, serializerClass);
        Registration registration = kryo.register(Payload.class);
        T serializer = serializerClass.cast(registration.getSerializer());

        Payload original = new Payload("serializer-factory");
        Output output = new Output(128, -1);
        kryo.writeObject(output, original);
        output.close();

        Input input = new Input(output.toBytes());
        Payload restored = kryo.readObject(input, Payload.class);
        input.close();

        assertThat(restored.value).isEqualTo(original.value);
        assertThat(serializer.writeCalls).isEqualTo(1);
        assertThat(serializer.readCalls).isEqualTo(1);
        return serializer;
    }

    public static class Payload {
        private String value;

        public Payload() {
        }

        Payload(String value) {
            this.value = value;
        }
    }

    public abstract static class RecordingPayloadSerializer extends Serializer<Payload> {
        final String constructorName;
        final Kryo constructedWithKryo;
        final Class<?> constructedForType;
        int writeCalls;
        int readCalls;

        RecordingPayloadSerializer(String constructorName, Kryo constructedWithKryo, Class<?> constructedForType) {
            this.constructorName = constructorName;
            this.constructedWithKryo = constructedWithKryo;
            this.constructedForType = constructedForType;
        }

        @Override
        public void write(Kryo kryo, Output output, Payload object) {
            writeCalls++;
            output.writeString(object.value);
        }

        @Override
        public Payload read(Kryo kryo, Input input, Class<Payload> type) {
            readCalls++;
            return new Payload(input.readString());
        }
    }

    public static class KryoAndTypePayloadSerializer extends RecordingPayloadSerializer {
        public KryoAndTypePayloadSerializer(Kryo kryo, Class<?> type) {
            super("kryo-and-type", kryo, type);
        }
    }

    public static class KryoOnlyPayloadSerializer extends RecordingPayloadSerializer {
        public KryoOnlyPayloadSerializer(Kryo kryo) {
            super("kryo-only", kryo, null);
        }
    }

    public static class TypeOnlyPayloadSerializer extends RecordingPayloadSerializer {
        public TypeOnlyPayloadSerializer(Class<?> type) {
            super("type-only", null, type);
        }
    }

    public static class NoArgumentPayloadSerializer extends RecordingPayloadSerializer {
        public NoArgumentPayloadSerializer() {
            super("no-argument", null, null);
        }
    }
}
