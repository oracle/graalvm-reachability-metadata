/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.factories.ReflectionSerializerFactory;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionSerializerFactoryTest {
    @Test
    void createsSerializerUsingKryoAndTypeConstructor() {
        Kryo kryo = new Kryo();

        Serializer serializer = new ReflectionSerializerFactory(KryoAndTypeSerializer.class)
                .makeSerializer(kryo, SampleRecord.class);

        KryoAndTypeSerializer created = (KryoAndTypeSerializer) serializer;
        assertThat(created.kryo).isSameAs(kryo);
        assertThat(created.type).isEqualTo(SampleRecord.class);
    }

    @Test
    void createsSerializerUsingKryoConstructorWhenTwoArgumentConstructorIsAbsent() {
        Kryo kryo = new Kryo();

        Serializer serializer = ReflectionSerializerFactory.makeSerializer(
                kryo,
                KryoOnlySerializer.class,
                SampleRecord.class);

        KryoOnlySerializer created = (KryoOnlySerializer) serializer;
        assertThat(created.kryo).isSameAs(kryo);
    }

    @Test
    void createsSerializerUsingTypeConstructorWhenKryoConstructorsAreAbsent() {
        Kryo kryo = new Kryo();

        Serializer serializer = ReflectionSerializerFactory.makeSerializer(
                kryo,
                TypeOnlySerializer.class,
                SampleRecord.class);

        TypeOnlySerializer created = (TypeOnlySerializer) serializer;
        assertThat(created.type).isEqualTo(SampleRecord.class);
    }

    public static class KryoAndTypeSerializer extends Serializer<SampleRecord> {
        final Kryo kryo;
        final Class<?> type;

        public KryoAndTypeSerializer(Kryo kryo, Class<?> type) {
            this.kryo = kryo;
            this.type = type;
        }

        @Override
        public void write(Kryo kryo, Output output, SampleRecord object) {
            output.writeString(object.value);
        }

        @Override
        public SampleRecord read(Kryo kryo, Input input, Class<SampleRecord> type) {
            return new SampleRecord(input.readString());
        }
    }

    public static class KryoOnlySerializer extends Serializer<SampleRecord> {
        final Kryo kryo;

        public KryoOnlySerializer(Kryo kryo) {
            this.kryo = kryo;
        }

        @Override
        public void write(Kryo kryo, Output output, SampleRecord object) {
            output.writeString(object.value);
        }

        @Override
        public SampleRecord read(Kryo kryo, Input input, Class<SampleRecord> type) {
            return new SampleRecord(input.readString());
        }
    }

    public static class TypeOnlySerializer extends Serializer<SampleRecord> {
        final Class<?> type;

        public TypeOnlySerializer(Class<?> type) {
            this.type = type;
        }

        @Override
        public void write(Kryo kryo, Output output, SampleRecord object) {
            output.writeString(object.value);
        }

        @Override
        public SampleRecord read(Kryo kryo, Input input, Class<SampleRecord> type) {
            return new SampleRecord(input.readString());
        }
    }

    public static class SampleRecord {
        final String value;

        SampleRecord(String value) {
            this.value = value;
        }
    }
}
