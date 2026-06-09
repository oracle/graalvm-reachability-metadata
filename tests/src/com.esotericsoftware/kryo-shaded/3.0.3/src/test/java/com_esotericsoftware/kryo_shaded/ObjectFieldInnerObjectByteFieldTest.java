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

public class ObjectFieldInnerObjectByteFieldTest {
    @Test
    void serializesReadsAndCopiesPrivateByteFieldWithObjectCachedField() {
        Kryo kryo = newKryoWithByteFieldSerializer();

        ByteFieldBean original = new ByteFieldBean((byte) 0xA5);
        byte[] bytes = writeObject(kryo, original);
        ByteFieldBean restored = kryo.readObject(new Input(bytes), ByteFieldBean.class);
        ByteFieldBean copied = kryo.copy(original);

        assertThat(restored.getValue()).isEqualTo(original.getValue());
        assertThat(copied).isNotSameAs(original);
        assertThat(copied.getValue()).isEqualTo(original.getValue());
    }

    @Test
    void getsPrivateByteFieldThroughObjectCachedFieldMethod() throws ReflectiveOperationException {
        Kryo kryo = newKryoWithByteFieldSerializer();
        CachedField cachedField = ((FieldSerializer<?>) kryo.getSerializer(ByteFieldBean.class)).getField("value");

        assertThat(invokeObjectByteFieldGetField(cachedField, new ByteFieldBean((byte) 42))).isEqualTo((byte) 42);
    }

    private static Kryo newKryoWithByteFieldSerializer() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(true);
        kryo.setReferences(false);
        kryo.setAsmEnabled(true);

        FieldSerializer<ByteFieldBean> serializer = new FieldSerializer<>(kryo, ByteFieldBean.class);
        Registration registration = kryo.register(ByteFieldBean.class, serializer);
        registration.setInstantiator(new ObjectInstantiator<ByteFieldBean>() {
            @Override
            public ByteFieldBean newInstance() {
                return new ByteFieldBean();
            }
        });

        assertThat(serializer.getField("value").getClass().getName())
                .isEqualTo("com.esotericsoftware.kryo.serializers.ObjectField$ObjectByteField");
        return kryo;
    }

    private static Object invokeObjectByteFieldGetField(CachedField cachedField, ByteFieldBean bean)
            throws ReflectiveOperationException {
        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);
        return getField.invoke(cachedField, bean);
    }

    private static byte[] writeObject(Kryo kryo, ByteFieldBean original) {
        Output output = new Output(32, -1);
        kryo.writeObject(output, original);
        output.close();
        return output.toBytes();
    }

    public static class ByteFieldBean {
        private byte value;

        public ByteFieldBean() {
        }

        ByteFieldBean(byte value) {
            this.value = value;
        }

        public byte getValue() {
            return value;
        }
    }
}
