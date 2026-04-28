/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.factories.ReflectionSerializerFactory;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.Test;

public class ReflectionSerializerFactoryTest {
    @Test
    void createsSerializerWithKryoConstructorWhenFullConstructorIsAbsent() {
        Kryo kryo = newKryo();
        ReflectionSerializerFactory factory = new ReflectionSerializerFactory(KryoOnlySerializer.class);

        Serializer<?> serializer = factory.makeSerializer(kryo, FactorySubject.class);

        assertThat(serializer).isInstanceOf(KryoOnlySerializer.class);
        KryoOnlySerializer created = (KryoOnlySerializer) serializer;
        assertThat(created.kryo).isSameAs(kryo);
        assertThat(created.roundTrip(new FactorySubject("kryo-constructor"))).isEqualTo("kryo-constructor");
    }

    @Test
    void createsSerializerWithTypeConstructorWhenKryoConstructorsAreAbsent() {
        Kryo kryo = newKryo();

        Serializer<?> serializer = ReflectionSerializerFactory.makeSerializer(
                kryo, TypeOnlySerializer.class, FactorySubject.class);

        assertThat(serializer).isInstanceOf(TypeOnlySerializer.class);
        TypeOnlySerializer created = (TypeOnlySerializer) serializer;
        assertThat(created.type).isSameAs(FactorySubject.class);
        assertThat(created.roundTrip(new FactorySubject("type-constructor"))).isEqualTo("type-constructor");
    }

    @Test
    void createsSerializerWithDefaultConstructorWhenSpecializedConstructorsAreAbsent() {
        Kryo kryo = newKryo();

        Serializer<?> serializer = ReflectionSerializerFactory.makeSerializer(
                kryo, DefaultConstructorSerializer.class, FactorySubject.class);

        assertThat(serializer).isInstanceOf(DefaultConstructorSerializer.class);
        DefaultConstructorSerializer created = (DefaultConstructorSerializer) serializer;
        assertThat(created.roundTrip(new FactorySubject("default-constructor"))).isEqualTo("default-constructor");
    }

    private static Kryo newKryo() {
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
        return kryo;
    }

    public static class FactorySubject {
        public String name;

        public FactorySubject() {
        }

        FactorySubject(String name) {
            this.name = name;
        }
    }

    public abstract static class FactorySubjectSerializer extends Serializer<FactorySubject> {
        @Override
        public void write(Kryo kryo, Output output, FactorySubject object) {
            output.writeString(object.name);
        }

        @Override
        public FactorySubject read(Kryo kryo, Input input, Class<FactorySubject> type) {
            return new FactorySubject(input.readString());
        }

        String roundTrip(FactorySubject subject) {
            Kryo kryo = newKryo();
            Output output = new Output(64, -1);
            write(kryo, output, subject);
            FactorySubject read = read(kryo, new Input(output.toBytes()), FactorySubject.class);
            return read.name;
        }
    }

    public static class KryoOnlySerializer extends FactorySubjectSerializer {
        final Kryo kryo;

        public KryoOnlySerializer(Kryo kryo) {
            this.kryo = kryo;
        }
    }

    public static class TypeOnlySerializer extends FactorySubjectSerializer {
        final Class<?> type;

        public TypeOnlySerializer(Class<?> type) {
            this.type = type;
        }
    }

    public static class DefaultConstructorSerializer extends FactorySubjectSerializer {
        public DefaultConstructorSerializer() {
        }
    }
}
