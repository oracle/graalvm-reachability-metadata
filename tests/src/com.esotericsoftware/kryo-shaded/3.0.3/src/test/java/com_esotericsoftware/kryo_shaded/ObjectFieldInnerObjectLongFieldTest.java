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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.ObjectInstantiator;

public class ObjectFieldInnerObjectLongFieldTest {
    @Test
    void serializesReadsAndCopiesPrivateLongFieldWithObjectCachedField() {
        Kryo kryo = newKryoWithLongFieldSerializer();

        LongFieldBean original = new LongFieldBean(9_876_543_210_123L);
        byte[] bytes = writeObject(kryo, original);
        LongFieldBean restored = kryo.readObject(new Input(bytes), LongFieldBean.class);
        LongFieldBean copied = kryo.copy(original);

        assertThat(restored.getValue()).isEqualTo(original.getValue());
        assertThat(copied).isNotSameAs(original);
        assertThat(copied.getValue()).isEqualTo(original.getValue());
    }

    @Test
    void serializesAndReadsPrivateLongFieldWithFixedWidthEncoding() throws ReflectiveOperationException {
        Kryo kryo = newKryoWithLongFieldSerializer();
        CachedField cachedField = ((FieldSerializer<?>) kryo.getSerializer(LongFieldBean.class)).getField("value");
        setVarIntsEnabled(cachedField, false);

        LongFieldBean original = new LongFieldBean(0x1020304050607080L);
        byte[] bytes = writeObject(kryo, original);
        LongFieldBean restored = kryo.readObject(new Input(bytes), LongFieldBean.class);

        assertThat(restored.getValue()).isEqualTo(original.getValue());
    }

    @Test
    void getsPrivateLongFieldThroughObjectCachedFieldMethod() throws ReflectiveOperationException {
        Kryo kryo = newKryoWithLongFieldSerializer();
        CachedField cachedField = ((FieldSerializer<?>) kryo.getSerializer(LongFieldBean.class)).getField("value");

        assertThat(invokeObjectLongFieldGetField(cachedField, new LongFieldBean(42L))).isEqualTo(42L);
    }

    private static Kryo newKryoWithLongFieldSerializer() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(true);
        kryo.setReferences(false);
        kryo.setAsmEnabled(true);

        FieldSerializer<LongFieldBean> serializer = new FieldSerializer<>(kryo, LongFieldBean.class);
        Registration registration = kryo.register(LongFieldBean.class, serializer);
        registration.setInstantiator(new ObjectInstantiator<LongFieldBean>() {
            @Override
            public LongFieldBean newInstance() {
                return new LongFieldBean();
            }
        });

        assertThat(serializer.getField("value").getClass().getName())
                .isEqualTo("com.esotericsoftware.kryo.serializers.ObjectField$ObjectLongField");
        return kryo;
    }

    private static void setVarIntsEnabled(CachedField cachedField, boolean varIntsEnabled)
            throws ReflectiveOperationException {
        Field varIntsEnabledField = CachedField.class.getDeclaredField("varIntsEnabled");
        varIntsEnabledField.setAccessible(true);
        varIntsEnabledField.setBoolean(cachedField, varIntsEnabled);
    }

    private static Object invokeObjectLongFieldGetField(CachedField cachedField, LongFieldBean bean)
            throws ReflectiveOperationException {
        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);
        return getField.invoke(cachedField, bean);
    }

    private static byte[] writeObject(Kryo kryo, LongFieldBean original) {
        Output output = new Output(32, -1);
        kryo.writeObject(output, original);
        output.close();
        return output.toBytes();
    }

    public static class LongFieldBean {
        private long value;

        public LongFieldBean() {
        }

        LongFieldBean(long value) {
            this.value = value;
        }

        public long getValue() {
            return value;
        }
    }
}
