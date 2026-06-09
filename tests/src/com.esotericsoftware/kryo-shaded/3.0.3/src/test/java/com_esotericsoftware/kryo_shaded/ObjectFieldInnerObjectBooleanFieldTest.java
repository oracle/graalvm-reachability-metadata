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
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.esotericsoftware.kryo.serializers.FieldSerializer.CachedField;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.ObjectInstantiator;

public class ObjectFieldInnerObjectBooleanFieldTest {
    @Test
    void serializesReadsAndCopiesPrivateBooleanFieldWithObjectCachedField() {
        Kryo kryo = newKryoWithBooleanFieldSerializer();

        BooleanFieldBean original = new BooleanFieldBean(true);
        byte[] bytes = writeObject(kryo, original);
        BooleanFieldBean restored = kryo.readObject(new Input(bytes), BooleanFieldBean.class);
        BooleanFieldBean copied = kryo.copy(original);

        assertThat(restored.isEnabled()).isEqualTo(original.isEnabled());
        assertThat(copied).isNotSameAs(original);
        assertThat(copied.isEnabled()).isEqualTo(original.isEnabled());
    }

    @Test
    void getsPrivateBooleanFieldThroughObjectCachedFieldMethod() throws ReflectiveOperationException {
        Kryo kryo = newKryoWithBooleanFieldSerializer();
        CachedField cachedField = ((FieldSerializer<?>) kryo.getSerializer(BooleanFieldBean.class)).getField("enabled");

        assertThat(invokeObjectBooleanFieldGetField(cachedField, new BooleanFieldBean(true))).isEqualTo(true);
    }

    private static Kryo newKryoWithBooleanFieldSerializer() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(true);
        kryo.setReferences(false);
        kryo.setAsmEnabled(true);

        FieldSerializer<BooleanFieldBean> serializer = new FieldSerializer<>(kryo, BooleanFieldBean.class);
        Registration registration = kryo.register(BooleanFieldBean.class, serializer);
        registration.setInstantiator(new ObjectInstantiator<BooleanFieldBean>() {
            @Override
            public BooleanFieldBean newInstance() {
                return new BooleanFieldBean();
            }
        });

        assertThat(serializer.getField("enabled").getClass().getName())
                .isEqualTo("com.esotericsoftware.kryo.serializers.ObjectField$ObjectBooleanField");
        return kryo;
    }

    private static Object invokeObjectBooleanFieldGetField(CachedField cachedField, BooleanFieldBean bean)
            throws ReflectiveOperationException {
        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);
        return getField.invoke(cachedField, bean);
    }

    private static byte[] writeObject(Kryo kryo, BooleanFieldBean original) {
        Output output = new Output(32, -1);
        kryo.writeObject(output, original);
        output.close();
        return output.toBytes();
    }

    public static class BooleanFieldBean {
        private boolean enabled;

        public BooleanFieldBean() {
        }

        BooleanFieldBean(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}
