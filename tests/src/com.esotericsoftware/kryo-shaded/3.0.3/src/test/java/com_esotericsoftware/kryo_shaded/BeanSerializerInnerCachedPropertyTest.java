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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanSerializerInnerCachedPropertyTest {
    private static final Map<Object, String> NAMES = Collections.synchronizedMap(new WeakHashMap<>());

    @BeforeEach
    void clearBeanState() {
        NAMES.clear();
    }

    @Test
    void serializesBeanPropertyInheritedFromDefaultInterfaceAccessors() {
        Kryo kryo = kryoWithBeanSerializer();
        DefaultAccessorBean original = new DefaultAccessorBean();
        original.setName("fallback-accessor");

        Output output = new Output(128, -1);
        kryo.writeObject(output, original);

        DefaultAccessorBean restored = kryo.readObject(new Input(output.toBytes()), DefaultAccessorBean.class);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.getName()).isEqualTo("fallback-accessor");
    }

    @Test
    void copiesBeanPropertyInheritedFromDefaultInterfaceAccessors() {
        Kryo kryo = kryoWithBeanSerializer();
        DefaultAccessorBean original = new DefaultAccessorBean();
        original.setName("copied-accessor");

        DefaultAccessorBean copied = kryo.copy(original);

        assertThat(copied).isNotSameAs(original);
        assertThat(copied.getName()).isEqualTo("copied-accessor");
    }

    private static Kryo kryoWithBeanSerializer() {
        Kryo kryo = new Kryo();
        kryo.register(DefaultAccessorBean.class, new BeanSerializer<>(kryo, DefaultAccessorBean.class));
        return kryo;
    }

    public interface DefaultAccessors {
        default String getName() {
            return NAMES.get(this);
        }

        default void setName(String name) {
            NAMES.put(this, name);
        }
    }

    public static class DefaultAccessorBean implements DefaultAccessors {
    }
}
