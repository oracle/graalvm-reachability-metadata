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

public class ObjectFieldInnerObjectFloatFieldTest {
    @Test
    void serializesReadsAndCopiesPrivateFloatFieldWithObjectCachedField() {
        Kryo kryo = newKryoWithFloatFieldSerializer();

        FloatFieldBean original = new FloatFieldBean(12345.625f);
        byte[] bytes = writeObject(kryo, original);
        FloatFieldBean restored = kryo.readObject(new Input(bytes), FloatFieldBean.class);
        FloatFieldBean copied = kryo.copy(original);

        assertThat(restored.getValue()).isEqualTo(original.getValue());
        assertThat(copied).isNotSameAs(original);
        assertThat(copied.getValue()).isEqualTo(original.getValue());
    }

    @Test
    void getsPrivateFloatFieldThroughObjectCachedFieldMethod() throws ReflectiveOperationException {
        Kryo kryo = newKryoWithFloatFieldSerializer();
        CachedField cachedField = ((FieldSerializer<?>) kryo.getSerializer(FloatFieldBean.class)).getField("value");

        assertThat(invokeObjectFloatFieldGetField(cachedField, new FloatFieldBean(3.5f))).isEqualTo(3.5f);
    }

    private static Kryo newKryoWithFloatFieldSerializer() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(true);
        kryo.setReferences(false);
        kryo.setAsmEnabled(true);

        FieldSerializer<FloatFieldBean> serializer = new FieldSerializer<>(kryo, FloatFieldBean.class);
        Registration registration = kryo.register(FloatFieldBean.class, serializer);
        registration.setInstantiator(new ObjectInstantiator<FloatFieldBean>() {
            @Override
            public FloatFieldBean newInstance() {
                return new FloatFieldBean();
            }
        });

        assertThat(serializer.getField("value").getClass().getName())
                .isEqualTo("com.esotericsoftware.kryo.serializers.ObjectField$ObjectFloatField");
        return kryo;
    }

    private static Object invokeObjectFloatFieldGetField(CachedField cachedField, FloatFieldBean bean)
            throws ReflectiveOperationException {
        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);
        return getField.invoke(cachedField, bean);
    }

    private static byte[] writeObject(Kryo kryo, FloatFieldBean original) {
        Output output = new Output(32, -1);
        kryo.writeObject(output, original);
        output.close();
        return output.toBytes();
    }

    public static class FloatFieldBean {
        private float value;

        public FloatFieldBean() {
        }

        FloatFieldBean(float value) {
            this.value = value;
        }

        public float getValue() {
            return value;
        }
    }
}
