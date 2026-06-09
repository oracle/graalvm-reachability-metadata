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
import com.esotericsoftware.kryo.serializers.BeanSerializer;
import org.junit.jupiter.api.Test;

public class BeanSerializerInnerCachedPropertyTest {
    @Test
    void serializesAndCopiesBeanPropertiesWithReflectionFallback() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setReferences(false);

        BeanSerializer<ReflectiveBean> serializer = new BeanSerializer<>(
                kryo, ReflectiveBean.class);
        kryo.register(ReflectiveBean.class, serializer);

        ReflectiveBean original = new ReflectiveBean();
        original.setName("alpha");
        original.setCount(42);

        byte[] bytes = writeObject(kryo, original);
        ReflectiveBean restored = kryo.readObject(new Input(bytes), ReflectiveBean.class);
        ReflectiveBean copied = kryo.copy(original);

        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.getCount()).isEqualTo(original.getCount());
        assertThat(copied.getName()).isEqualTo(original.getName());
        assertThat(copied.getCount()).isEqualTo(original.getCount());
    }

    private static byte[] writeObject(Kryo kryo, ReflectiveBean original) {
        Output output = new Output(128, -1);
        kryo.writeObject(output, original);
        output.close();
        return output.toBytes();
    }

    public static class ReflectiveBean {
        private String name;
        private int count;

        public ReflectiveBean() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    /**
     * Occupies the accessor class name that ReflectASM derives from {@link ReflectiveBean},
     * so BeanSerializer uses its documented reflection fallback when bytecode access is
     * unavailable.
     */
    public static class ReflectiveBeanMethodAccess {
        public ReflectiveBeanMethodAccess() {
        }
    }

    /**
     * Occupies the constructor accessor class name that ReflectASM derives from {@link ReflectiveBean},
     * so Kryo falls back to reflective construction when bytecode generation is unavailable.
     */
    public static class ReflectiveBeanConstructorAccess {
        public ReflectiveBeanConstructorAccess() {
        }
    }
}
