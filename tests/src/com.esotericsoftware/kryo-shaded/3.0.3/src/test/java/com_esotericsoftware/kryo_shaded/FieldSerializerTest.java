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

public class FieldSerializerTest {
    @Test
    void serializesAndCopiesDeclaredFieldsWhenAsmIsDisabled() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setReferences(false);
        kryo.setAsmEnabled(false);

        FieldSerializer<FieldSerializerBean> serializer = new FieldSerializer<>(kryo, FieldSerializerBean.class);
        kryo.register(FieldSerializerBean.class, serializer);

        FieldSerializerBean original = new FieldSerializerBean(101, "alpha", true, 123456789L, 77);
        byte[] bytes = writeObject(kryo, original);
        FieldSerializerBean restored = kryo.readObject(new Input(bytes), FieldSerializerBean.class);
        FieldSerializerBean copied = kryo.copy(original);

        assertThat(serializer.getFields()).hasSize(4);
        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.isEnabled()).isEqualTo(original.isEnabled());
        assertThat(restored.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(restored.getTransientScore()).isZero();
        assertThat(copied.getTransientScore()).isEqualTo(original.getTransientScore());
    }

    private static byte[] writeObject(Kryo kryo, FieldSerializerBean original) {
        Output output = new Output(128, -1);
        kryo.writeObject(output, original);
        output.close();
        return output.toBytes();
    }

    public static class FieldSerializerParent {
        private long createdAt;

        public FieldSerializerParent() {
        }

        FieldSerializerParent(long createdAt) {
            this.createdAt = createdAt;
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }

    public static class FieldSerializerBean extends FieldSerializerParent {
        private int id;
        private String name;
        private boolean enabled;
        private transient int transientScore;

        public FieldSerializerBean() {
        }

        FieldSerializerBean(int id, String name, boolean enabled, long createdAt, int transientScore) {
            super(createdAt);
            this.id = id;
            this.name = name;
            this.enabled = enabled;
            this.transientScore = transientScore;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getTransientScore() {
            return transientScore;
        }
    }

    /**
     * Occupies the constructor accessor class name that ReflectASM derives from
     * {@link FieldSerializerBean}, so Kryo falls back to reflective construction.
     */
    public static class FieldSerializerBeanConstructorAccess {
        public FieldSerializerBeanConstructorAccess() {
        }
    }
}
