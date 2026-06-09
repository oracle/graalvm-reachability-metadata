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
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.ObjectInstantiator;

public class ObjectFieldInnerObjectIntFieldTest {
    @Test
    void serializesReadsAndCopiesPrivateIntFieldWithObjectCachedField() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(true);
        kryo.setReferences(false);
        kryo.setAsmEnabled(true);

        FieldSerializer<IntFieldBean> serializer = new FieldSerializer<>(kryo, IntFieldBean.class);
        Registration registration = kryo.register(IntFieldBean.class, serializer);
        registration.setInstantiator(new ObjectInstantiator<IntFieldBean>() {
            @Override
            public IntFieldBean newInstance() {
                return new IntFieldBean();
            }
        });

        assertThat(serializer.getField("value").getClass().getName())
                .isEqualTo("com.esotericsoftware.kryo.serializers.ObjectField$ObjectIntField");

        IntFieldBean original = new IntFieldBean(270_544_960);
        byte[] bytes = writeObject(kryo, original);
        IntFieldBean restored = kryo.readObject(new Input(bytes), IntFieldBean.class);
        IntFieldBean copied = kryo.copy(original);

        assertThat(restored.getValue()).isEqualTo(original.getValue());
        assertThat(copied).isNotSameAs(original);
        assertThat(copied.getValue()).isEqualTo(original.getValue());
    }

    private static byte[] writeObject(Kryo kryo, IntFieldBean original) {
        Output output = new Output(32, -1);
        kryo.writeObject(output, original);
        output.close();
        return output.toBytes();
    }

    public static class IntFieldBean {
        private int value;

        public IntFieldBean() {
        }

        IntFieldBean(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
