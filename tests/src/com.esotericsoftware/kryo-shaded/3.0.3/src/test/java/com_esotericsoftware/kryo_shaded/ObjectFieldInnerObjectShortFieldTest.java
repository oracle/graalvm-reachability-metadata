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

public class ObjectFieldInnerObjectShortFieldTest {
    @BeforeEach
    void disableRuntimeCodeGeneration() {
        Util.isAndroid = true;
    }

    @Test
    void serializesReadsAndCopiesPrimitiveShortFieldUsingObjectFieldSerializer() {
        Kryo kryo = newKryoUsingObjectFields();
        FieldSerializer<ShortHolder> serializer = newObjectFieldSerializer(kryo);
        kryo.register(ShortHolder.class, serializer);

        ShortHolder original = new ShortHolder((short) -30_303);
        Output output = new Output(16, -1);
        kryo.writeObject(output, original, serializer);
        output.close();

        Input input = new Input(output.toBytes());
        ShortHolder restored = kryo.readObject(input, ShortHolder.class, serializer);
        input.close();
        ShortHolder copied = serializer.copy(kryo, original);

        assertThat(restored.value).isEqualTo(original.value);
        assertThat(copied).isNotSameAs(original);
        assertThat(copied.value).isEqualTo(original.value);
        assertThat(serializer.getField("value").getClass().getName()).endsWith("ObjectField$ObjectShortField");
        assertThat(serializer.getUseAsmEnabled()).isTrue();
    }

    @Test
    void exposesPrimitiveShortFieldValueThroughObjectFieldAccessor() throws Throwable {
        FieldSerializer<ShortHolder> serializer = newObjectFieldSerializer(newKryoUsingObjectFields());
        FieldSerializer.CachedField cachedField = serializer.getField("value");
        ShortHolder holder = new ShortHolder(Short.MAX_VALUE);

        Object value = objectShortFieldGetter(cachedField).invoke(cachedField, holder);

        assertThat(value).isEqualTo(holder.value);
        assertThat(cachedField.getClass().getName()).endsWith("ObjectField$ObjectShortField");
    }

    private static MethodHandle objectShortFieldGetter(FieldSerializer.CachedField cachedField) throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(cachedField.getClass(), MethodHandles.lookup());
        MethodType methodType = MethodType.methodType(Object.class, Object.class);
        return lookup.findVirtual(cachedField.getClass(), "getField", methodType);
    }

    private static FieldSerializer<ShortHolder> newObjectFieldSerializer(Kryo kryo) {
        FieldSerializer<ShortHolder> serializer = new FieldSerializer<>(kryo, ShortHolder.class);
        serializer.setUseAsm(true);
        return serializer;
    }

    private static Kryo newKryoUsingObjectFields() {
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
        kryo.setAsmEnabled(false);
        return kryo;
    }

    public static class ShortHolder {
        public short value;

        public ShortHolder() {
        }

        ShortHolder(short value) {
            this.value = value;
        }
    }
}
