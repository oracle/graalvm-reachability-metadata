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

public class ObjectFieldInnerObjectByteFieldTest {
    @Test
    void writesAndReadsPrivateByteField() {
        FieldSerializer<ByteRecord> serializer = newByteRecordSerializer();
        CachedField<?> statusField = serializer.getField("status");
        ByteRecord source = new ByteRecord((byte)42);
        ByteRecord target = new ByteRecord((byte)-1);

        Output output = new Output(1, -1);
        statusField.write(output, source);
        statusField.read(new Input(output.toBytes()), target);

        assertThat(statusField.getClass().getName()).endsWith("ObjectField$ObjectByteField");
        assertThat(output.toBytes()).containsExactly(source.getStatus());
        assertThat(target.getStatus()).isEqualTo(source.getStatus());
    }

    @Test
    void copiesPrivateByteFieldBetweenObjects() {
        FieldSerializer<ByteRecord> serializer = newByteRecordSerializer();
        CachedField<?> statusField = serializer.getField("status");
        ByteRecord source = new ByteRecord(Byte.MIN_VALUE);
        ByteRecord target = new ByteRecord(Byte.MAX_VALUE);

        statusField.copy(source, target);

        assertThat(statusField.getClass().getName()).endsWith("ObjectField$ObjectByteField");
        assertThat(target.getStatus()).isEqualTo(source.getStatus());
    }

    @Test
    void exposesPrivateByteValueThroughCachedObjectField() throws Exception {
        FieldSerializer<ByteRecord> serializer = newByteRecordSerializer();
        CachedField<?> statusField = serializer.getField("status");
        ByteRecord source = new ByteRecord((byte)7);

        Object fieldValue = readObjectFieldValue(statusField, source);

        assertThat(statusField.getClass().getName()).endsWith("ObjectField$ObjectByteField");
        assertThat(fieldValue).isEqualTo(source.getStatus());
    }

    private static FieldSerializer<ByteRecord> newByteRecordSerializer() {
        Kryo kryo = new Kryo();
        kryo.setAsmEnabled(true);
        return new FieldSerializer<>(kryo, ByteRecord.class);
    }

    private static Object readObjectFieldValue(CachedField<?> cachedField, ByteRecord record) throws Exception {
        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);
        return getField.invoke(cachedField, record);
    }

    public static class ByteRecord {
        private byte status;

        public ByteRecord() {
        }

        ByteRecord(byte status) {
            this.status = status;
        }

        byte getStatus() {
            return status;
        }
    }
}
