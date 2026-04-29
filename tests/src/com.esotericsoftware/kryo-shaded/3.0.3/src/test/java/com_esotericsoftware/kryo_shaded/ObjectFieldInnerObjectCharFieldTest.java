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

public class ObjectFieldInnerObjectCharFieldTest {
    @Test
    void writesAndReadsPrivateCharField() {
        FieldSerializer<CharRecord> serializer = newCharRecordSerializer();
        CachedField<?> markerField = serializer.getField("marker");
        CharRecord source = new CharRecord('λ');
        CharRecord target = new CharRecord('x');

        Output output = new Output(Character.BYTES, -1);
        markerField.write(output, source);
        markerField.read(new Input(output.toBytes()), target);

        assertThat(markerField.getClass().getName()).endsWith("ObjectField$ObjectCharField");
        assertThat(output.toBytes()).hasSize(Character.BYTES);
        assertThat(target.getMarker()).isEqualTo(source.getMarker());
    }

    @Test
    void copiesPrivateCharFieldBetweenObjects() {
        FieldSerializer<CharRecord> serializer = newCharRecordSerializer();
        CachedField<?> markerField = serializer.getField("marker");
        CharRecord source = new CharRecord(Character.MAX_VALUE);
        CharRecord target = new CharRecord(Character.MIN_VALUE);

        markerField.copy(source, target);

        assertThat(markerField.getClass().getName()).endsWith("ObjectField$ObjectCharField");
        assertThat(target.getMarker()).isEqualTo(source.getMarker());
    }

    @Test
    void exposesPrivateCharValueThroughCachedObjectField() throws Exception {
        FieldSerializer<CharRecord> serializer = newCharRecordSerializer();
        CachedField<?> markerField = serializer.getField("marker");
        CharRecord source = new CharRecord('Ω');

        Object fieldValue = readObjectFieldValue(markerField, source);

        assertThat(markerField.getClass().getName()).endsWith("ObjectField$ObjectCharField");
        assertThat(fieldValue).isEqualTo(source.getMarker());
    }

    private static FieldSerializer<CharRecord> newCharRecordSerializer() {
        Kryo kryo = new Kryo();
        kryo.setAsmEnabled(true);
        return new FieldSerializer<>(kryo, CharRecord.class);
    }

    private static Object readObjectFieldValue(CachedField<?> cachedField, CharRecord record) throws Exception {
        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);
        return getField.invoke(cachedField, record);
    }

    public static class CharRecord {
        private char marker;

        public CharRecord() {
        }

        CharRecord(char marker) {
            this.marker = marker;
        }

        char getMarker() {
            return marker;
        }
    }
}
