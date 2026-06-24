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

public class ObjectFieldInnerObjectBooleanFieldTest {
    @BeforeEach
    void disableRuntimeCodeGeneration() {
        Util.isAndroid = true;
    }

    @Test
    void serializesReadsAndCopiesPrimitiveBooleanFieldUsingObjectFieldSerializer() {
        Kryo kryo = newKryoUsingObjectFields();
        FieldSerializer<BooleanHolder> serializer = newObjectFieldSerializer(kryo);
        kryo.register(BooleanHolder.class, serializer);

        BooleanHolder original = new BooleanHolder(true);
        Output output = new Output(16, -1);
        kryo.writeObject(output, original, serializer);
        output.close();

        Input input = new Input(output.toBytes());
        BooleanHolder restored = kryo.readObject(input, BooleanHolder.class, serializer);
        input.close();
        BooleanHolder copied = serializer.copy(kryo, original);

        assertThat(restored.value).isTrue();
        assertThat(copied).isNotSameAs(original);
        assertThat(copied.value).isTrue();
        assertThat(serializer.getField("value").getClass().getName()).endsWith("ObjectField$ObjectBooleanField");
        assertThat(serializer.getUseAsmEnabled()).isTrue();
    }

    @Test
    void exposesPrimitiveBooleanFieldValueThroughObjectFieldAccessor() throws Throwable {
        FieldSerializer<BooleanHolder> serializer = newObjectFieldSerializer(newKryoUsingObjectFields());
        FieldSerializer.CachedField cachedField = serializer.getField("value");
        BooleanHolder holder = new BooleanHolder(true);

        Object value = objectBooleanFieldGetter(cachedField).invoke(cachedField, holder);

        assertThat(value).isEqualTo(holder.value);
        assertThat(cachedField.getClass().getName()).endsWith("ObjectField$ObjectBooleanField");
    }

    private static MethodHandle objectBooleanFieldGetter(FieldSerializer.CachedField cachedField) throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(cachedField.getClass(), MethodHandles.lookup());
        MethodType methodType = MethodType.methodType(Object.class, Object.class);
        return lookup.findVirtual(cachedField.getClass(), "getField", methodType);
    }

    private static FieldSerializer<BooleanHolder> newObjectFieldSerializer(Kryo kryo) {
        FieldSerializer<BooleanHolder> serializer = new FieldSerializer<>(kryo, BooleanHolder.class);
        serializer.setUseAsm(true);
        return serializer;
    }

    private static Kryo newKryoUsingObjectFields() {
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
        kryo.setAsmEnabled(false);
        return kryo;
    }

    public static class BooleanHolder {
        public boolean value;

        public BooleanHolder() {
        }

        BooleanHolder(boolean value) {
            this.value = value;
        }
    }
}
