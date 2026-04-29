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

public class ObjectFieldInnerObjectDoubleFieldTest {
    @Test
    void writesAndReadsPrivateDoubleField() {
        FieldSerializer<DoubleRecord> serializer = newDoubleRecordSerializer();
        CachedField<?> scoreField = serializer.getField("score");
        DoubleRecord source = new DoubleRecord(12345.6789d);
        DoubleRecord target = new DoubleRecord(-1.0d);

        Output output = new Output(Double.BYTES, -1);
        scoreField.write(output, source);
        scoreField.read(new Input(output.toBytes()), target);

        assertThat(scoreField.getClass().getName()).endsWith("ObjectField$ObjectDoubleField");
        assertThat(output.toBytes()).hasSize(Double.BYTES);
        assertThat(target.getScore()).isEqualTo(source.getScore());
    }

    @Test
    void copiesPrivateDoubleFieldBetweenObjects() {
        FieldSerializer<DoubleRecord> serializer = newDoubleRecordSerializer();
        CachedField<?> scoreField = serializer.getField("score");
        DoubleRecord source = new DoubleRecord(Math.PI);
        DoubleRecord target = new DoubleRecord(Math.E);

        scoreField.copy(source, target);

        assertThat(scoreField.getClass().getName()).endsWith("ObjectField$ObjectDoubleField");
        assertThat(target.getScore()).isEqualTo(source.getScore());
    }

    @Test
    void exposesPrivateDoubleValueThroughCachedObjectField() throws Exception {
        FieldSerializer<DoubleRecord> serializer = newDoubleRecordSerializer();
        CachedField<?> scoreField = serializer.getField("score");
        DoubleRecord source = new DoubleRecord(-9876.54321d);

        Object fieldValue = readObjectFieldValue(scoreField, source);

        assertThat(scoreField.getClass().getName()).endsWith("ObjectField$ObjectDoubleField");
        assertThat(fieldValue).isEqualTo(source.getScore());
    }

    private static FieldSerializer<DoubleRecord> newDoubleRecordSerializer() {
        Kryo kryo = new Kryo();
        kryo.setAsmEnabled(true);
        return new FieldSerializer<>(kryo, DoubleRecord.class);
    }

    private static Object readObjectFieldValue(CachedField<?> cachedField, DoubleRecord record) throws Exception {
        Method getField = cachedField.getClass().getMethod("getField", Object.class);
        getField.setAccessible(true);
        return getField.invoke(cachedField, record);
    }

    public static class DoubleRecord {
        private double score;

        public DoubleRecord() {
        }

        DoubleRecord(double score) {
            this.score = score;
        }

        double getScore() {
            return score;
        }
    }
}
