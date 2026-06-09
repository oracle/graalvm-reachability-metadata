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

public class ObjectFieldInnerObjectCharFieldTest {
    @Test
    void serializesReadsAndCopiesPrivateCharFieldWithObjectCachedField() {
        Kryo kryo = newKryoWithCharFieldSerializer();

        CharFieldBean original = new CharFieldBean('\u03A9');
        byte[] bytes = writeObject(kryo, original);
        CharFieldBean restored = kryo.readObject(new Input(bytes), CharFieldBean.class);
        CharFieldBean copied = kryo.copy(original);

        assertThat(restored.getValue()).isEqualTo(original.getValue());
        assertThat(copied).isNotSameAs(original);
        assertThat(copied.getValue()).isEqualTo(original.getValue());
    }

    @Test
    void getsPrivateCharFieldThroughObjectCachedFieldMethod() throws ReflectiveOperationException {
        Kryo kryo = newKryoWithCharFieldSerializer();
        CachedField cachedField = ((FieldSerializer<?>) kryo.getSerializer(CharFieldBean.class)).getField("value");

        assertThat(invokeObjectCharFieldGetField(cachedField, new CharFieldBean('K'))).isEqualTo('K');
    }

    private static Kryo newKryoWithCharFieldSerializer() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(true);
        kryo.setReferences(false);
        kryo.setAsmEnabled(true);

        FieldSerializer<CharFieldBean> serializer = new FieldSerializer<>(kryo, CharFieldBean.class);
        Registration registration = kryo.register(CharFieldBean.class, serializer);
        registration.setInstantiator(new ObjectInstantiator<CharFieldBean>() {
            @Override
            public CharFieldBean newInstance() {
                return new CharFieldBean();
            }
        });

        assertThat(serializer.getField("value").getClass().getName())
                .isEqualTo("com.esotericsoftware.kryo.serializers.ObjectField$ObjectCharField");
        return kryo;
    }

    private static Object invokeObjectCharFieldGetField(CachedField cachedField, CharFieldBean bean)
            throws ReflectiveOperationException {
        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);
        return getField.invoke(cachedField, bean);
    }

    private static byte[] writeObject(Kryo kryo, CharFieldBean original) {
        Output output = new Output(32, -1);
        kryo.writeObject(output, original);
        output.close();
        return output.toBytes();
    }

    public static class CharFieldBean {
        private char value;

        public CharFieldBean() {
        }

        CharFieldBean(char value) {
            this.value = value;
        }

        public char getValue() {
            return value;
        }
    }
}
