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
import com.esotericsoftware.kryo.util.Util;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ObjectFieldInnerObjectByteFieldTest {
    @BeforeEach
    void disableRuntimeCodeGeneration() {
        Util.isAndroid = true;
    }

    @Test
    void serializesReadsAndCopiesPrimitiveByteFieldUsingObjectFieldSerializer() {
        Kryo kryo = newKryoUsingObjectFields();
        FieldSerializer<ByteHolder> serializer = newObjectFieldSerializer(kryo);
        kryo.register(ByteHolder.class, serializer);

        ByteHolder original = new ByteHolder((byte) -83);
        Output output = new Output(16, -1);
        kryo.writeObject(output, original, serializer);
        output.close();

        Input input = new Input(output.toBytes());
        ByteHolder restored = kryo.readObject(input, ByteHolder.class, serializer);
        input.close();
        ByteHolder copied = serializer.copy(kryo, original);

        assertThat(restored.value).isEqualTo(original.value);
        assertThat(copied).isNotSameAs(original);
        assertThat(copied.value).isEqualTo(original.value);
        assertThat(serializer.getField("value").getClass().getName()).endsWith("ObjectField$ObjectByteField");
        assertThat(serializer.getUseAsmEnabled()).isTrue();
    }

    @Test
    void exposesPrimitiveByteFieldValueThroughObjectFieldAccessor() throws Throwable {
        FieldSerializer<ByteHolder> serializer = newObjectFieldSerializer(newKryoUsingObjectFields());
        FieldSerializer.CachedField cachedField = serializer.getField("value");
        ByteHolder holder = new ByteHolder(Byte.MAX_VALUE);

        Object value = objectByteFieldGetter(cachedField).invoke(cachedField, holder);

        assertThat(value).isEqualTo(holder.value);
        assertThat(cachedField.getClass().getName()).endsWith("ObjectField$ObjectByteField");
    }

    private static MethodHandle objectByteFieldGetter(FieldSerializer.CachedField cachedField) throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(cachedField.getClass(), MethodHandles.lookup());
        MethodType methodType = MethodType.methodType(Object.class, Object.class);
        return lookup.findVirtual(cachedField.getClass(), "getField", methodType);
    }

    private static FieldSerializer<ByteHolder> newObjectFieldSerializer(Kryo kryo) {
        FieldSerializer<ByteHolder> serializer = new FieldSerializer<>(kryo, ByteHolder.class);
        serializer.setUseAsm(true);
        return serializer;
    }

    private static Kryo newKryoUsingObjectFields() {
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
        kryo.setAsmEnabled(false);
        return kryo;
    }

    public static class ByteHolder {
        public byte value;

        public ByteHolder() {
        }

        ByteHolder(byte value) {
            this.value = value;
        }
    }
}
