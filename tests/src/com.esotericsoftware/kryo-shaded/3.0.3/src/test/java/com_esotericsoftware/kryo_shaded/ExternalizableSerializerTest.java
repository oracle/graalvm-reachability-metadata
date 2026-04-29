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
    void roundTripsExternalizableWithoutSerializationReplacementHooks() {
        Kryo kryo = newKryo();
        ExternalizableSerializer serializer = new ExternalizableSerializer();
        PlainExternalizable original = new PlainExternalizable("payload", 42);

        PlainExternalizable read = roundTrip(kryo, serializer, original, PlainExternalizable.class);

        assertThat(read).isNotSameAs(original);
        assertThat(read.label).isEqualTo(original.label);
        assertThat(read.value).isEqualTo(original.value);
        assertThat(original.writeExternalCalls).isEqualTo(1);
        assertThat(read.readExternalCalls).isEqualTo(1);
    }

    @Test
    void usesJavaSerializationWhenExternalizableHasInheritedReadResolve() {
        Kryo kryo = newKryo();
        ExternalizableSerializer serializer = new ExternalizableSerializer();
        ResolvingExternalizable original = new ResolvingExternalizable("replacement");

        Object read = roundTripObject(kryo, serializer, original, ResolvingExternalizable.class);

        assertThat(read).isEqualTo("resolved:replacement");
        assertThat(original.writeExternalCalls).isEqualTo(1);
    }

    private static Kryo newKryo() {
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
        return kryo;
    }

    private static <T> T roundTrip(Kryo kryo, ExternalizableSerializer serializer, T original, Class<T> type) {
        return type.cast(roundTripObject(kryo, serializer, original, type));
    }

    private static Object roundTripObject(Kryo kryo, ExternalizableSerializer serializer, Object original, Class<?> type) {
        Output output = new Output(128, -1);
        serializer.write(kryo, output, original);
        kryo.reset();

        Input input = new Input(output.toBytes());
        Object read = serializer.read(kryo, input, type);
        kryo.reset();
        return read;
    }

    public static class PlainExternalizable implements Externalizable {
        private String label;
        private int value;
        private int writeExternalCalls;
        private int readExternalCalls;

        public PlainExternalizable() {
        }

        PlainExternalizable(String label, int value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public void writeExternal(ObjectOutput output) throws IOException {
            writeExternalCalls++;
            output.writeUTF(label);
            output.writeInt(value);
        }

        @Override
        public void readExternal(ObjectInput input) throws IOException {
            label = input.readUTF();
            value = input.readInt();
            readExternalCalls++;
        }
    }

    public static class ResolvingExternalizable extends InheritedReadResolve implements Externalizable {
        private String value;
        private int writeExternalCalls;

        public ResolvingExternalizable() {
        }

        ResolvingExternalizable(String value) {
            this.value = value;
        }

        @Override
        public void writeExternal(ObjectOutput output) throws IOException {
            writeExternalCalls++;
            output.writeUTF(value);
        }

        @Override
        public void readExternal(ObjectInput input) throws IOException {
            value = input.readUTF();
        }

        @Override
        protected String valueForReadResolve() {
            return value;
        }
    }

    public abstract static class InheritedReadResolve {
        protected Object readResolve() {
            return "resolved:" + valueForReadResolve();
        }

        protected abstract String valueForReadResolve();
    }
}
