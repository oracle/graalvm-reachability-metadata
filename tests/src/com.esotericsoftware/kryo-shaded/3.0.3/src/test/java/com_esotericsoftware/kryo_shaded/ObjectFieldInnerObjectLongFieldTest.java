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
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectFieldInnerObjectLongFieldTest {
    @Test
    void writesAndReadsPrivateLongFieldWithVariableLengthEncoding() {
        FieldSerializer<LongRecord> serializer = newLongRecordSerializer();
        CachedField<?> totalField = serializer.getField("total");
        LongRecord source = new LongRecord(9_876_543_210L);
        LongRecord target = new LongRecord();

        Output output = new Output(16, -1);
        totalField.write(output, source);
        totalField.read(new Input(output.toBytes()), target);

        assertThat(totalField.getClass().getName()).endsWith("ObjectField$ObjectLongField");
        assertThat(target.getTotal()).isEqualTo(source.getTotal());
    }

    @Test
    void writesAndReadsPrivateLongFieldWithFixedLengthEncoding() throws Exception {
        FieldSerializer<LongRecord> serializer = newLongRecordSerializer();
        CachedField<?> totalField = serializer.getField("total");
        setVarIntsEnabled(totalField, false);
        LongRecord source = new LongRecord(Long.MAX_VALUE - 42L);
        LongRecord target = new LongRecord();

        Output output = new Output(16, -1);
        totalField.write(output, source);
        totalField.read(new Input(output.toBytes()), target);

        assertThat(totalField.getClass().getName()).endsWith("ObjectField$ObjectLongField");
        assertThat(target.getTotal()).isEqualTo(source.getTotal());
        assertThat(output.toBytes()).hasSize(Long.BYTES);
    }

    @Test
    void copiesPrivateLongFieldBetweenObjects() {
        FieldSerializer<LongRecord> serializer = newLongRecordSerializer();
        CachedField<?> totalField = serializer.getField("total");
        LongRecord source = new LongRecord(-4_294_967_296L);
        LongRecord target = new LongRecord(1L);

        totalField.copy(source, target);

        assertThat(totalField.getClass().getName()).endsWith("ObjectField$ObjectLongField");
        assertThat(target.getTotal()).isEqualTo(source.getTotal());
    }

    @Test
    void exposesPrivateLongValueThroughCachedObjectField() throws Exception {
        FieldSerializer<LongRecord> serializer = newLongRecordSerializer();
        CachedField<?> totalField = serializer.getField("total");
        LongRecord source = new LongRecord(1_234_567_890_123L);

        Object reflectedValue = readObjectFieldValue(totalField, source);

        assertThat(totalField.getClass().getName()).endsWith("ObjectField$ObjectLongField");
        assertThat(reflectedValue).isEqualTo(source.getTotal());
    }

    private static FieldSerializer<LongRecord> newLongRecordSerializer() {
        Kryo kryo = new Kryo();
        kryo.setAsmEnabled(true);
        return new FieldSerializer<>(kryo, LongRecord.class);
    }

    private static Object readObjectFieldValue(CachedField<?> cachedField, LongRecord record) throws Exception {
        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);
        return getField.invoke(cachedField, record);
    }

    private static void setVarIntsEnabled(CachedField<?> cachedField, boolean enabled) throws Exception {
        Field varIntsEnabled = CachedField.class.getDeclaredField("varIntsEnabled");
        varIntsEnabled.setAccessible(true);
        varIntsEnabled.setBoolean(cachedField, enabled);
    }

    public static class LongRecord {
        private long total;

        public LongRecord() {
        }

        LongRecord(long total) {
            this.total = total;
        }

        long getTotal() {
            return total;
        }
    }
}
