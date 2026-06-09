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

public class ObjectFieldInnerObjectShortFieldTest {
    @Test
    void serializesReadsAndCopiesPrivateShortFieldWithObjectCachedField() {
        Kryo kryo = newKryoWithShortFieldSerializer();

        ShortFieldBean original = new ShortFieldBean((short) 0x2345);
        byte[] bytes = writeObject(kryo, original);
        ShortFieldBean restored = kryo.readObject(new Input(bytes), ShortFieldBean.class);
        ShortFieldBean copied = kryo.copy(original);

        assertThat(restored.getValue()).isEqualTo(original.getValue());
        assertThat(copied).isNotSameAs(original);
        assertThat(copied.getValue()).isEqualTo(original.getValue());
    }

    @Test
    void getsPrivateShortFieldThroughObjectCachedFieldMethod() throws ReflectiveOperationException {
        Kryo kryo = newKryoWithShortFieldSerializer();
        CachedField cachedField = ((FieldSerializer<?>) kryo.getSerializer(ShortFieldBean.class)).getField("value");

        assertThat(invokeObjectShortFieldGetField(cachedField, new ShortFieldBean((short) 42))).isEqualTo((short) 42);
    }

    private static Kryo newKryoWithShortFieldSerializer() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(true);
        kryo.setReferences(false);
        kryo.setAsmEnabled(true);

        FieldSerializer<ShortFieldBean> serializer = new FieldSerializer<>(kryo, ShortFieldBean.class);
        Registration registration = kryo.register(ShortFieldBean.class, serializer);
        registration.setInstantiator(new ObjectInstantiator<ShortFieldBean>() {
            @Override
            public ShortFieldBean newInstance() {
                return new ShortFieldBean();
            }
        });

        assertThat(serializer.getField("value").getClass().getName())
                .isEqualTo("com.esotericsoftware.kryo.serializers.ObjectField$ObjectShortField");
        return kryo;
    }

    private static Object invokeObjectShortFieldGetField(CachedField cachedField, ShortFieldBean bean)
            throws ReflectiveOperationException {
        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);
        return getField.invoke(cachedField, bean);
    }

    private static byte[] writeObject(Kryo kryo, ShortFieldBean original) {
        Output output = new Output(32, -1);
        kryo.writeObject(output, original);
        output.close();
        return output.toBytes();
    }

    public static class ShortFieldBean {
        private short value;

        public ShortFieldBean() {
        }

        ShortFieldBean(short value) {
            this.value = value;
        }

        public short getValue() {
            return value;
        }
    }
}
