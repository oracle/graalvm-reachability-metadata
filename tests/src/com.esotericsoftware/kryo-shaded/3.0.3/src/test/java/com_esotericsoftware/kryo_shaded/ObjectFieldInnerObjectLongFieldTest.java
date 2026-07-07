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
import java.lang.invoke.VarHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ObjectFieldInnerObjectLongFieldTest {
    @BeforeEach
    void disableRuntimeCodeGeneration() {
        Util.isAndroid = true;
    }

    @Test
    void serializesReadsAndCopiesPrimitiveLongFieldUsingObjectFieldSerializer() {
        Kryo kryo = newKryoUsingObjectFields();
        FieldSerializer<LongHolder> serializer = new FieldSerializer<>(kryo, LongHolder.class);
        kryo.register(LongHolder.class, serializer);

        LongHolder original = new LongHolder(-9_876_543_210_123L);
        Output output = new Output(64, -1);
        kryo.writeObject(output, original, serializer);
        output.close();

        Input input = new Input(output.toBytes());
        LongHolder restored = kryo.readObject(input, LongHolder.class, serializer);
        input.close();
        LongHolder copied = serializer.copy(kryo, original);

        assertThat(restored.value).isEqualTo(original.value);
        assertThat(copied).isNotSameAs(original);
        assertThat(copied.value).isEqualTo(original.value);
        assertThat(serializer.getField("value").getClass().getName()).endsWith("ObjectField$ObjectLongField");
        assertThat(serializer.getUseAsmEnabled()).isTrue();
    }

    @Test
    void serializesPrimitiveLongFieldWithFixedWidthEncoding() throws Throwable {
        Kryo kryo = newKryoUsingObjectFields();
        FieldSerializer<LongHolder> serializer = new FieldSerializer<>(kryo, LongHolder.class);
        kryo.register(LongHolder.class, serializer);
        FieldSerializer.CachedField cachedField = serializer.getField("value");
        setVarIntsEnabled(cachedField, false);

        LongHolder original = new LongHolder(Long.MIN_VALUE + 303L);
        Output output = new Output(64, -1);
        kryo.writeObject(output, original, serializer);
        output.close();

        Input input = new Input(output.toBytes());
        LongHolder restored = kryo.readObject(input, LongHolder.class, serializer);
        input.close();

        assertThat(restored.value).isEqualTo(original.value);
        assertThat(output.toBytes()).hasSize(Long.BYTES);
        assertThat(cachedField.getClass().getName()).endsWith("ObjectField$ObjectLongField");
    }

    @Test
    void exposesPrimitiveLongFieldValueThroughObjectFieldAccessor() throws Throwable {
        FieldSerializer<LongHolder> serializer = new FieldSerializer<>(newKryoUsingObjectFields(), LongHolder.class);
        FieldSerializer.CachedField cachedField = serializer.getField("value");
        LongHolder holder = new LongHolder(4_242_424_242L);

        Object value = objectLongFieldGetter(cachedField).invoke(cachedField, holder);

        assertThat(value).isEqualTo(holder.value);
        assertThat(cachedField.getClass().getName()).endsWith("ObjectField$ObjectLongField");
    }

    private static void setVarIntsEnabled(
            FieldSerializer.CachedField cachedField, boolean varIntsEnabled) throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                FieldSerializer.CachedField.class, MethodHandles.lookup());
        VarHandle handle = lookup.findVarHandle(FieldSerializer.CachedField.class, "varIntsEnabled", boolean.class);
        handle.set(cachedField, varIntsEnabled);
    }

    private static MethodHandle objectLongFieldGetter(FieldSerializer.CachedField cachedField) throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(cachedField.getClass(), MethodHandles.lookup());
        MethodType methodType = MethodType.methodType(Object.class, Object.class);
        return lookup.findVirtual(cachedField.getClass(), "getField", methodType);
    }

    private static Kryo newKryoUsingObjectFields() {
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
        kryo.setAsmEnabled(true);
        return kryo;
    }

    public static class LongHolder {
        public long value;

        public LongHolder() {
        }

        LongHolder(long value) {
            this.value = value;
        }
    }
}
