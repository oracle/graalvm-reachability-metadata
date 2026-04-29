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
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.esotericsoftware.kryo.serializers.FieldSerializer.CachedField;
import com.esotericsoftware.kryo.util.IntMap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectFieldInnerObjectIntFieldTest {
    @Test
    void writesAndReadsPrivateIntFieldWithVariableLengthEncoding() throws Exception {
        FieldSerializer<IntMap> serializer = newIntMapSerializer();
        CachedField<?> capacityField = serializer.getField("capacity");
        IntMap<String> source = mapWithCapacityAndValues(32);
        IntMap<String> target = new IntMap<>(2);

        Output output = new Output(32, -1);
        capacityField.write(output, source);
        capacityField.read(new Input(output.toBytes()), target);

        assertThat(capacityField.getClass().getName()).endsWith("ObjectField$ObjectIntField");
        assertThat(readIntValue(capacityField, target)).isEqualTo(readIntValue(capacityField, source));
        assertThat(target.size).isZero();
    }

    @Test
    void writesAndReadsPrivateIntFieldWithFixedLengthEncoding() throws Exception {
        FieldSerializer<IntMap> serializer = newIntMapSerializer();
        CachedField<?> thresholdField = serializer.getField("threshold");
        setVarIntsEnabled(thresholdField, false);
        IntMap<String> source = mapWithCapacityAndValues(64);
        IntMap<String> target = new IntMap<>(4);

        Output output = new Output(32, -1);
        thresholdField.write(output, source);
        thresholdField.read(new Input(output.toBytes()), target);

        assertThat(thresholdField.getClass().getName()).endsWith("ObjectField$ObjectIntField");
        assertThat(readIntValue(thresholdField, target)).isEqualTo(readIntValue(thresholdField, source));
        assertThat(output.toBytes()).hasSize(Integer.BYTES);
    }

    @Test
    void copiesPrivateIntFieldBetweenMaps() throws Exception {
        FieldSerializer<IntMap> serializer = newIntMapSerializer();
        CachedField<?> maskField = serializer.getField("mask");
        IntMap<String> source = mapWithCapacityAndValues(128);
        IntMap<String> target = new IntMap<>(1);

        maskField.copy(source, target);

        assertThat(maskField.getClass().getName()).endsWith("ObjectField$ObjectIntField");
        assertThat(readIntValue(maskField, target)).isEqualTo(readIntValue(maskField, source));
        assertThat(target.size).isZero();
    }

    private static FieldSerializer<IntMap> newIntMapSerializer() {
        Kryo kryo = new Kryo();
        kryo.setAsmEnabled(true);
        return new FieldSerializer<>(kryo, IntMap.class);
    }

    private static IntMap<String> mapWithCapacityAndValues(int initialCapacity) {
        IntMap<String> map = new IntMap<>(initialCapacity);
        map.put(0, "zero");
        map.put(7, "seven");
        map.put(42, "answer");
        return map;
    }

    private static int readIntValue(CachedField<?> cachedField, IntMap<String> map) throws Exception {
        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);
        return (Integer)getField.invoke(cachedField, map);
    }

    private static void setVarIntsEnabled(CachedField<?> cachedField, boolean enabled) throws Exception {
        Field varIntsEnabled = CachedField.class.getDeclaredField("varIntsEnabled");
        varIntsEnabled.setAccessible(true);
        varIntsEnabled.setBoolean(cachedField, enabled);
    }
}
