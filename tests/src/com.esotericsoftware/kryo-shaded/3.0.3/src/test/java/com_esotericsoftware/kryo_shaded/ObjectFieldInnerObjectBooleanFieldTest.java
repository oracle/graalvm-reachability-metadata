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

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectFieldInnerObjectBooleanFieldTest {
    @Test
    void writesAndReadsPrivateBooleanField() {
        FieldSerializer<BooleanRecord> serializer = newBooleanRecordSerializer();
        CachedField<?> activeField = serializer.getField("active");
        BooleanRecord source = new BooleanRecord(true);
        BooleanRecord target = new BooleanRecord(false);

        Output output = new Output(1, -1);
        activeField.write(output, source);
        activeField.read(new Input(output.toBytes()), target);

        assertThat(activeField.getClass().getName()).endsWith("ObjectField$ObjectBooleanField");
        assertThat(target.isActive()).isTrue();
    }

    @Test
    void copiesPrivateBooleanFieldBetweenObjects() {
        FieldSerializer<BooleanRecord> serializer = newBooleanRecordSerializer();
        CachedField<?> activeField = serializer.getField("active");
        BooleanRecord source = new BooleanRecord(true);
        BooleanRecord target = new BooleanRecord(false);

        activeField.copy(source, target);

        assertThat(activeField.getClass().getName()).endsWith("ObjectField$ObjectBooleanField");
        assertThat(target.isActive()).isEqualTo(source.isActive());
    }

    @Test
    void exposesPrivateBooleanValueThroughCachedObjectField() throws Exception {
        FieldSerializer<BooleanRecord> serializer = newBooleanRecordSerializer();
        CachedField<?> activeField = serializer.getField("active");
        BooleanRecord source = new BooleanRecord(true);

        Object fieldValue = readObjectFieldValue(activeField, source);

        assertThat(activeField.getClass().getName()).endsWith("ObjectField$ObjectBooleanField");
        assertThat(fieldValue).isEqualTo(source.isActive());
    }

    private static FieldSerializer<BooleanRecord> newBooleanRecordSerializer() {
        Kryo kryo = new Kryo();
        kryo.setAsmEnabled(true);
        return new FieldSerializer<>(kryo, BooleanRecord.class);
    }

    private static Object readObjectFieldValue(CachedField<?> cachedField, BooleanRecord record) throws Exception {
        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);
        return getField.invoke(cachedField, record);
    }

    public static class BooleanRecord {
        private boolean active;

        public BooleanRecord() {
        }

        BooleanRecord(boolean active) {
            this.active = active;
        }

        boolean isActive() {
            return active;
        }
    }
}
