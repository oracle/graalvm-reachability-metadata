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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectFieldInnerObjectFloatFieldTest {
    private static final String OBJECT_FLOAT_FIELD_CLASS_NAME =
        "com.esotericsoftware.kryo.serializers.ObjectField$ObjectFloatField";
    private static final MethodHandle OBJECT_FLOAT_GET_FIELD = objectFloatGetField();

    @Test
    void writesAndReadsPrivateFloatField() {
        FieldSerializer<FloatRecord> serializer = newFloatRecordSerializer();
        CachedField<?> ratioField = serializer.getField("ratio");
        FloatRecord source = new FloatRecord(123.5f);
        FloatRecord target = new FloatRecord(-1.0f);

        Output output = new Output(Float.BYTES, -1);
        ratioField.write(output, source);
        ratioField.read(new Input(output.toBytes()), target);

        assertThat(ratioField.getClass().getName()).endsWith("ObjectField$ObjectFloatField");
        assertThat(output.toBytes()).hasSize(Float.BYTES);
        assertThat(target.getRatio()).isEqualTo(source.getRatio());
    }

    @Test
    void copiesPrivateFloatFieldBetweenObjects() {
        FieldSerializer<FloatRecord> serializer = newFloatRecordSerializer();
        CachedField<?> ratioField = serializer.getField("ratio");
        FloatRecord source = new FloatRecord(3.14159f);
        FloatRecord target = new FloatRecord(2.71828f);

        ratioField.copy(source, target);

        assertThat(ratioField.getClass().getName()).endsWith("ObjectField$ObjectFloatField");
        assertThat(target.getRatio()).isEqualTo(source.getRatio());
    }

    @Test
    void exposesPrivateFloatValueThroughCachedObjectField() {
        FieldSerializer<FloatRecord> serializer = newFloatRecordSerializer();
        CachedField<?> ratioField = serializer.getField("ratio");
        FloatRecord source = new FloatRecord(-9876.5f);

        Object fieldValue = readObjectFieldValue(ratioField, source);

        assertThat(ratioField.getClass().getName()).endsWith("ObjectField$ObjectFloatField");
        assertThat(fieldValue).isEqualTo(source.getRatio());
    }

    private static FieldSerializer<FloatRecord> newFloatRecordSerializer() {
        Kryo kryo = new Kryo();
        kryo.setAsmEnabled(true);
        return new FieldSerializer<>(kryo, FloatRecord.class);
    }

    private static Object readObjectFieldValue(CachedField<?> cachedField, FloatRecord record) {
        try {
            return OBJECT_FLOAT_GET_FIELD.invoke(cachedField, record);
        } catch (RuntimeException | Error throwable) {
            throw throwable;
        } catch (Throwable throwable) {
            throw new AssertionError("Unable to invoke ObjectFloatField#getField", throwable);
        }
    }

    private static MethodHandle objectFloatGetField() {
        try {
            Class<?> objectFloatFieldClass = Class.forName(OBJECT_FLOAT_FIELD_CLASS_NAME);
            return MethodHandles.privateLookupIn(objectFloatFieldClass, MethodHandles.lookup())
                .findVirtual(objectFloatFieldClass, "getField", MethodType.methodType(Object.class, Object.class));
        } catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    public static class FloatRecord {
        private float ratio;

        public FloatRecord() {
        }

        FloatRecord(float ratio) {
            this.ratio = ratio;
        }

        float getRatio() {
            return ratio;
        }
    }
}
