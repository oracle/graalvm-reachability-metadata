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
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import org.junit.jupiter.api.Test;

public class ObjectFieldTest {
    @Test
    void serializesReadsAndCopiesPrivateObjectFieldWithObjectCachedField() {
        Kryo kryo = newKryoWithObjectFieldSerializer();

        ObjectFieldBean original = new ObjectFieldBean("alpha");
        byte[] bytes = writeObject(kryo, original);
        ObjectFieldBean restored = kryo.readObject(new Input(bytes), ObjectFieldBean.class);
        ObjectFieldBean copied = kryo.copy(original);

        assertThat(restored.getValue()).isEqualTo(original.getValue());
        assertThat(copied).isNotSameAs(original);
        assertThat(copied.getValue()).isEqualTo(original.getValue());
    }

    private static Kryo newKryoWithObjectFieldSerializer() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(true);
        kryo.setReferences(false);
        kryo.setAsmEnabled(true);

        FieldSerializer<ObjectFieldBean> serializer = new FieldSerializer<>(kryo, ObjectFieldBean.class);
        kryo.register(ObjectFieldBean.class, serializer);

        assertThat(serializer.getField("value").getClass().getName())
                .isEqualTo("com.esotericsoftware.kryo.serializers.ObjectField");
        return kryo;
    }

    private static byte[] writeObject(Kryo kryo, ObjectFieldBean original) {
        Output output = new Output(32, -1);
        kryo.writeObject(output, original);
        output.close();
        return output.toBytes();
    }

    public static class ObjectFieldBean {
        private String value;

        public ObjectFieldBean() {
        }

        ObjectFieldBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Occupies the constructor accessor class name that ReflectASM derives from
     * {@link ObjectFieldBean}, so Kryo falls back to reflective construction.
     */
    public static class ObjectFieldBeanConstructorAccess {
        public ObjectFieldBeanConstructorAccess() {
        }
    }
}
