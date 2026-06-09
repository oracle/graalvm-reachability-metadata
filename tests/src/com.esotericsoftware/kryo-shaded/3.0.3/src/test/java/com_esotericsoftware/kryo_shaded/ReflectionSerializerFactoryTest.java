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
    void createsSerializerWithKryoAndClassConstructor() {
        Kryo kryo = new Kryo();

        Serializer<?> serializer = ReflectionSerializerFactory.makeSerializer(
                kryo, KryoAndClassSerializer.class, Bean.class);

        assertThat(serializer).isInstanceOf(KryoAndClassSerializer.class);
        KryoAndClassSerializer typedSerializer = (KryoAndClassSerializer) serializer;
        assertThat(typedSerializer.getKryo()).isSameAs(kryo);
        assertThat(typedSerializer.getType()).isSameAs(Bean.class);
    }

    @Test
    void createsSerializerWithKryoConstructor() {
        Kryo kryo = new Kryo();

        Serializer<?> serializer = ReflectionSerializerFactory.makeSerializer(
                kryo, KryoOnlySerializer.class, Bean.class);

        assertThat(serializer).isInstanceOf(KryoOnlySerializer.class);
        assertThat(((KryoOnlySerializer) serializer).getKryo()).isSameAs(kryo);
    }

    @Test
    void createsSerializerWithClassConstructor() {
        Kryo kryo = new Kryo();

        Serializer<?> serializer = ReflectionSerializerFactory.makeSerializer(
                kryo, ClassOnlySerializer.class, Bean.class);

        assertThat(serializer).isInstanceOf(ClassOnlySerializer.class);
        assertThat(((ClassOnlySerializer) serializer).getType()).isSameAs(Bean.class);
    }

    @Test
    void createsSerializerWithNoArgumentConstructor() {
        Kryo kryo = new Kryo();

        Serializer<?> serializer = ReflectionSerializerFactory.makeSerializer(
                kryo, NoArgumentSerializer.class, Bean.class);

        assertThat(serializer).isInstanceOf(NoArgumentSerializer.class);
    }

    public static class KryoAndClassSerializer extends TestSerializer {
        private final Kryo kryo;
        private final Class<?> type;

        public KryoAndClassSerializer(Kryo kryo, Class<?> type) {
            this.kryo = kryo;
            this.type = type;
        }

        public Kryo getKryo() {
            return kryo;
        }

        public Class<?> getType() {
            return type;
        }
    }

    public static class KryoOnlySerializer extends TestSerializer {
        private final Kryo kryo;

        public KryoOnlySerializer(Kryo kryo) {
            this.kryo = kryo;
        }

        public Kryo getKryo() {
            return kryo;
        }
    }

    public static class ClassOnlySerializer extends TestSerializer {
        private final Class<?> type;

        public ClassOnlySerializer(Class<?> type) {
            this.type = type;
        }

        public Class<?> getType() {
            return type;
        }
    }

    public static class NoArgumentSerializer extends TestSerializer {
        public NoArgumentSerializer() {
        }
    }

    public abstract static class TestSerializer extends Serializer<Bean> {
        @Override
        public void write(Kryo kryo, Output output, Bean object) {
        }

        @Override
        public Bean read(Kryo kryo, Input input, Class<Bean> type) {
            return new Bean();
        }
    }

    public static class Bean {
        public Bean() {
        }
    }
}
