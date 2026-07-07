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

public class ObjectFieldInnerObjectFloatFieldTest {
    @BeforeEach
    void disableRuntimeCodeGeneration() {
        Util.isAndroid = true;
    }

    @Test
    void serializesReadsAndCopiesPrimitiveFloatFieldUsingObjectFieldSerializer() {
        Kryo kryo = newKryoUsingObjectFields();
        FieldSerializer<FloatHolder> serializer = newObjectFieldSerializer(kryo);
        kryo.register(FloatHolder.class, serializer);

        FloatHolder original = new FloatHolder(-303.25f);
        Output output = new Output(32, -1);
        kryo.writeObject(output, original, serializer);
        output.close();

        Input input = new Input(output.toBytes());
        FloatHolder restored = kryo.readObject(input, FloatHolder.class, serializer);
        input.close();
        FloatHolder copied = serializer.copy(kryo, original);

        assertThat(restored.value).isEqualTo(original.value);
        assertThat(copied).isNotSameAs(original);
        assertThat(copied.value).isEqualTo(original.value);
        assertThat(output.toBytes()).hasSize(Float.BYTES);
        assertThat(serializer.getField("value").getClass().getName()).endsWith("ObjectField$ObjectFloatField");
        assertThat(serializer.getUseAsmEnabled()).isTrue();
    }

    @Test
    void exposesPrimitiveFloatFieldValueThroughObjectFieldAccessor() throws Throwable {
        FieldSerializer<FloatHolder> serializer = newObjectFieldSerializer(newKryoUsingObjectFields());
        FieldSerializer.CachedField cachedField = serializer.getField("value");
        FloatHolder holder = new FloatHolder(Float.MAX_VALUE / 303.0f);

        Object value = objectFloatFieldGetter(cachedField).invoke(cachedField, holder);

        assertThat(value).isEqualTo(holder.value);
        assertThat(cachedField.getClass().getName()).endsWith("ObjectField$ObjectFloatField");
    }

    private static MethodHandle objectFloatFieldGetter(FieldSerializer.CachedField cachedField) throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(cachedField.getClass(), MethodHandles.lookup());
        MethodType methodType = MethodType.methodType(Object.class, Object.class);
        return lookup.findVirtual(cachedField.getClass(), "getField", methodType);
    }

    private static FieldSerializer<FloatHolder> newObjectFieldSerializer(Kryo kryo) {
        FieldSerializer<FloatHolder> serializer = new FieldSerializer<>(kryo, FloatHolder.class);
        serializer.setUseAsm(true);
        return serializer;
    }

    private static Kryo newKryoUsingObjectFields() {
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
        kryo.setAsmEnabled(false);
        return kryo;
    }

    public static class FloatHolder {
        public float value;

        public FloatHolder() {
        }

        FloatHolder(float value) {
            this.value = value;
        }
    }
}
