/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ExternalizableSerializer;
import org.junit.jupiter.api.Test;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

public class ExternalizableSerializerTest {
    @Test
    void usesJavaSerializationWhenAnInheritedWriteReplaceMethodIsPresent() {
        ExternalizableSerializer serializer = new ExternalizableSerializer();
        ReplaceableExternalizable original = new ReplaceableExternalizable("replacement-value", 73);
        Output output = new Output(128, -1);

        serializer.write(new Kryo(), output, original);

        Object restored = serializer.read(new Kryo(), new Input(output.toBytes()), ReplaceableExternalizable.class);

        assertThat(restored).isInstanceOf(ReplacementPayload.class);
        ReplacementPayload payload = (ReplacementPayload) restored;
        assertThat(payload.name).isEqualTo("replacement-value");
        assertThat(payload.count).isEqualTo(73);
    }

    public abstract static class ExternalizableWithInheritedReplacement implements Externalizable {
        protected String name;
        protected int count;

        public ExternalizableWithInheritedReplacement() {
        }

        ExternalizableWithInheritedReplacement(String name, int count) {
            this.name = name;
            this.count = count;
        }

        protected Object writeReplace() {
            return new ReplacementPayload(name, count);
        }
    }

    public static class ReplaceableExternalizable extends ExternalizableWithInheritedReplacement {
        public ReplaceableExternalizable() {
        }

        ReplaceableExternalizable(String name, int count) {
            super(name, count);
        }

        @Override
        public void writeExternal(ObjectOutput output) throws IOException {
            throw new IOException("writeReplace should route this type through Java serialization");
        }

        @Override
        public void readExternal(ObjectInput input) throws IOException {
            throw new IOException("writeReplace should route this type through Java serialization");
        }
    }

    public static class ReplacementPayload implements Serializable {
        private static final long serialVersionUID = 1L;

        public String name;
        public int count;

        public ReplacementPayload() {
        }

        ReplacementPayload(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }
}
