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

public class ObjectFieldInnerObjectDoubleFieldTest {
    @BeforeEach
    void disableRuntimeCodeGeneration() {
        Util.isAndroid = true;
    }

    @Test
    void serializesReadsAndCopiesPrimitiveDoubleFieldUsingObjectFieldSerializer() {
        Kryo kryo = newKryoUsingObjectFields();
        FieldSerializer<DoubleHolder> serializer = newObjectFieldSerializer(kryo);
        kryo.register(DoubleHolder.class, serializer);

        DoubleHolder original = new DoubleHolder(-Math.PI * 303.0d);
        Output output = new Output(32, -1);
        kryo.writeObject(output, original, serializer);
        output.close();

        Input input = new Input(output.toBytes());
        DoubleHolder restored = kryo.readObject(input, DoubleHolder.class, serializer);
        input.close();
        DoubleHolder copied = serializer.copy(kryo, original);

        assertThat(restored.value).isEqualTo(original.value);
        assertThat(copied).isNotSameAs(original);
        assertThat(copied.value).isEqualTo(original.value);
        assertThat(output.toBytes()).hasSize(Double.BYTES);
        assertThat(serializer.getField("value").getClass().getName()).endsWith("ObjectField$ObjectDoubleField");
        assertThat(serializer.getUseAsmEnabled()).isTrue();
    }

    @Test
    void exposesPrimitiveDoubleFieldValueThroughObjectFieldAccessor() throws Throwable {
        FieldSerializer<DoubleHolder> serializer = newObjectFieldSerializer(newKryoUsingObjectFields());
        FieldSerializer.CachedField cachedField = serializer.getField("value");
        DoubleHolder holder = new DoubleHolder(Double.MAX_VALUE / 303.0d);

        Object value = objectDoubleFieldGetter(cachedField).invoke(cachedField, holder);

        assertThat(value).isEqualTo(holder.value);
        assertThat(cachedField.getClass().getName()).endsWith("ObjectField$ObjectDoubleField");
    }

    private static MethodHandle objectDoubleFieldGetter(FieldSerializer.CachedField cachedField) throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(cachedField.getClass(), MethodHandles.lookup());
        MethodType methodType = MethodType.methodType(Object.class, Object.class);
        return lookup.findVirtual(cachedField.getClass(), "getField", methodType);
    }

    private static FieldSerializer<DoubleHolder> newObjectFieldSerializer(Kryo kryo) {
        FieldSerializer<DoubleHolder> serializer = new FieldSerializer<>(kryo, DoubleHolder.class);
        serializer.setUseAsm(true);
        return serializer;
    }

    private static Kryo newKryoUsingObjectFields() {
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
        kryo.setAsmEnabled(false);
        return kryo;
    }

    public static class DoubleHolder {
        public double value;

        public DoubleHolder() {
        }

        DoubleHolder(double value) {
            this.value = value;
        }
    }
}
