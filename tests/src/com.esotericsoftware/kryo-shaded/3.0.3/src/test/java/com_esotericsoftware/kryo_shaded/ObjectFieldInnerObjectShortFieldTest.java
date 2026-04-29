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

public class ObjectFieldInnerObjectShortFieldTest {
    @Test
    void writesAndReadsPrivateShortField() {
        FieldSerializer<ShortRecord> serializer = newShortRecordSerializer();
        CachedField<?> codeField = serializer.getField("code");
        ShortRecord source = new ShortRecord((short)12345);
        ShortRecord target = new ShortRecord((short)-1);

        Output output = new Output(Short.BYTES, -1);
        codeField.write(output, source);
        codeField.read(new Input(output.toBytes()), target);

        assertThat(codeField.getClass().getName()).endsWith("ObjectField$ObjectShortField");
        assertThat(output.toBytes()).hasSize(Short.BYTES);
        assertThat(target.getCode()).isEqualTo(source.getCode());
    }

    @Test
    void copiesPrivateShortFieldBetweenObjects() {
        FieldSerializer<ShortRecord> serializer = newShortRecordSerializer();
        CachedField<?> codeField = serializer.getField("code");
        ShortRecord source = new ShortRecord(Short.MIN_VALUE);
        ShortRecord target = new ShortRecord(Short.MAX_VALUE);

        codeField.copy(source, target);

        assertThat(codeField.getClass().getName()).endsWith("ObjectField$ObjectShortField");
        assertThat(target.getCode()).isEqualTo(source.getCode());
    }

    @Test
    void exposesPrivateShortValueThroughCachedObjectField() throws Exception {
        FieldSerializer<ShortRecord> serializer = newShortRecordSerializer();
        CachedField<?> codeField = serializer.getField("code");
        ShortRecord source = new ShortRecord((short)314);

        Object fieldValue = readObjectFieldValue(codeField, source);

        assertThat(codeField.getClass().getName()).endsWith("ObjectField$ObjectShortField");
        assertThat(fieldValue).isEqualTo(source.getCode());
    }

    private static FieldSerializer<ShortRecord> newShortRecordSerializer() {
        Kryo kryo = new Kryo();
        kryo.setAsmEnabled(true);
        return new FieldSerializer<>(kryo, ShortRecord.class);
    }

    private static Object readObjectFieldValue(CachedField<?> cachedField, ShortRecord record) throws Exception {
        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);
        return getField.invoke(cachedField, record);
    }

    public static class ShortRecord {
        private short code;

        public ShortRecord() {
        }

        ShortRecord(short code) {
            this.code = code;
        }

        short getCode() {
            return code;
        }
    }
}
