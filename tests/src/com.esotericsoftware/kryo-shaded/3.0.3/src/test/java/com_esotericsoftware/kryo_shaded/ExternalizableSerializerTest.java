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
import com.esotericsoftware.kryo.serializers.ExternalizableSerializer;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import org.junit.jupiter.api.Test;

public class ExternalizableSerializerTest {
    @Test
    void roundTripsExternalizableObjectThroughObjectInputAndOutputAdapters() {
        Kryo writer = createKryo(ExternalizableMessage.class);
        ExternalizableMessage original = new ExternalizableMessage("alpha", 17);

        Output output = new Output(128, -1);
        writer.writeObject(output, original);
        output.close();

        Kryo reader = createKryo(ExternalizableMessage.class);
        ExternalizableMessage restored = reader.readObject(new Input(output.toBytes()), ExternalizableMessage.class);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.getCount()).isEqualTo(original.getCount());
    }

    @Test
    void fallsBackToJavaSerializationWhenExternalizableDeclaresWriteReplace() {
        Kryo writer = createKryo(ReplacementAwareExternalizableMessage.class);
        ReplacementAwareExternalizableMessage original = new ReplacementAwareExternalizableMessage("beta", 23);

        Output output = new Output(256, -1);
        writer.writeObject(output, original);
        output.close();

        Kryo reader = createKryo(ReplacementAwareExternalizableMessage.class);
        ReplacementAwareExternalizableMessage restored = reader.readObject(
                new Input(output.toBytes()), ReplacementAwareExternalizableMessage.class);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.getCount()).isEqualTo(original.getCount());
    }

    private static Kryo createKryo(Class<?> externalizableType) {
        Kryo kryo = new Kryo();
        kryo.register(externalizableType, new ExternalizableSerializer());
        return kryo;
    }

    public static class ExternalizableMessage extends AbstractExternalizableMessage {
        public ExternalizableMessage() {
        }

        ExternalizableMessage(String name, int count) {
            super(name, count);
        }
    }

    /**
     * Occupies the constructor accessor class name that ReflectASM derives from
     * {@link ExternalizableMessage}, so Kryo falls back to reflective construction.
     */
    public static class ExternalizableMessageConstructorAccess {
        public ExternalizableMessageConstructorAccess() {
        }
    }

    public static class ReplacementAwareExternalizableMessage extends AbstractExternalizableMessage {
        public ReplacementAwareExternalizableMessage() {
        }

        ReplacementAwareExternalizableMessage(String name, int count) {
            super(name, count);
        }

        public Object writeReplace() {
            return this;
        }
    }

    public abstract static class AbstractExternalizableMessage implements Externalizable {
        private String name;
        private int count;

        protected AbstractExternalizableMessage() {
        }

        AbstractExternalizableMessage(String name, int count) {
            this.name = name;
            this.count = count;
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeUTF(name);
            out.writeInt(count);
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException {
            name = in.readUTF();
            count = in.readInt();
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }
    }
}
