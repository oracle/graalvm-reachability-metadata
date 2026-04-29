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
import com.esotericsoftware.kryo.serializers.BeanSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanSerializerInnerCachedPropertyTest {
    @Test
    void serializesBeanPropertyWhenReflectAsmAccessCannotBeCreated() {
        Kryo kryo = kryoWithBeanSerializer();
        ReflectAsmFallbackBean original = new ReflectAsmFallbackBean();
        original.setName("fallback-accessor");

        Output output = new Output(128, -1);
        kryo.writeObject(output, original);

        ReflectAsmFallbackBean restored = kryo.readObject(new Input(output.toBytes()), ReflectAsmFallbackBean.class);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.getName()).isEqualTo("fallback-accessor");
    }

    @Test
    void copiesBeanPropertyWhenReflectAsmAccessCannotBeCreated() {
        Kryo kryo = kryoWithBeanSerializer();
        ReflectAsmFallbackBean original = new ReflectAsmFallbackBean();
        original.setName("copied-accessor");

        ReflectAsmFallbackBean copied = kryo.copy(original);

        assertThat(copied).isNotSameAs(original);
        assertThat(copied.getName()).isEqualTo("copied-accessor");
    }

    private static Kryo kryoWithBeanSerializer() {
        Kryo kryo = new Kryo();
        kryo.register(ReflectAsmFallbackBean.class, new BeanSerializer<>(kryo, ReflectAsmFallbackBean.class));
        return kryo;
    }

    public static class ReflectAsmFallbackBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class ReflectAsmFallbackBeanMethodAccess {
    }
}
