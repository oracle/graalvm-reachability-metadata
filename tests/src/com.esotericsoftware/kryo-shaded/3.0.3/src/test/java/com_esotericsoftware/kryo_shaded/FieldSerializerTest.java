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
import com.esotericsoftware.reflectasm.ConstructorAccess;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class FieldSerializerTest {
    @Test
    void serializesAndCopiesFieldsDeclaredAcrossTheClassHierarchy() {
        Kryo kryo = newKryo();
        FieldSerializer<FieldSerializerSubject> serializer = new FieldSerializer<>(kryo, FieldSerializerSubject.class);
        FieldSerializerSubject original = FieldSerializerSubject.createSample();

        FieldSerializerSubject read = roundTrip(kryo, serializer, original, FieldSerializerSubject.class);
        FieldSerializerSubject copy = serializer.copy(kryo, original);

        assertThat(fieldNames(serializer)).contains("active", "createdAt", "id", "name", "score");
        assertThat(read.id).isEqualTo(original.id);
        assertThat(read.name).isEqualTo(original.name);
        assertThat(read.active).isEqualTo(original.active);
        assertThat(read.score).isEqualTo(original.score);
        assertThat(read.createdAt).isEqualTo(original.createdAt);
        assertThat(read.memo).isNull();
        assertThat(copy.id).isEqualTo(original.id);
        assertThat(copy.name).isEqualTo(original.name);
        assertThat(copy.active).isEqualTo(original.active);
        assertThat(copy.score).isEqualTo(original.score);
        assertThat(copy.createdAt).isEqualTo(original.createdAt);
        assertThat(copy.memo).isEqualTo(original.memo);
    }

    @Test
    void rebuildsCachedFieldsWhenUsingPublicSerializerConfiguration() {
        Kryo kryo = newKryo();
        kryo.setAsmEnabled(false);
        FieldSerializer<FieldSerializerSubject> serializer = new FieldSerializer<>(kryo, FieldSerializerSubject.class);

        serializer.setFieldsCanBeNull(false);
        serializer.setFixedFieldTypes(true);
        serializer.setIgnoreSyntheticFields(true);
        serializer.setUseAsm(false);

        FieldSerializerSubject original = FieldSerializerSubject.createSample();
        FieldSerializerSubject read = roundTrip(kryo, serializer, original, FieldSerializerSubject.class);

        assertThat(serializer.getField("name").toString()).isEqualTo("name");
        assertThat(read.name).isEqualTo(original.name);
        assertThat(read.id).isEqualTo(original.id);
        assertThat(read.score).isEqualTo(original.score);
    }

    @Test
    void rebuildsCachedFieldsUsingUnsafeMemoryRegions() throws Exception {
        Kryo kryo = newKryo();
        kryo.setAsmEnabled(false);
        FieldSerializer<PrimitiveRegionSubject> serializer = new FieldSerializer<>(kryo, PrimitiveRegionSubject.class);
        enableMemoryRegions(serializer);

        serializer.setUseAsm(false);
        PrimitiveRegionSubject original = PrimitiveRegionSubject.createSample();
        PrimitiveRegionSubject read = roundTrip(kryo, serializer, original, PrimitiveRegionSubject.class);

        assertThat(serializer.getUseMemRegions()).isTrue();
        assertThat(read.first).isEqualTo(original.first);
        assertThat(read.second).isEqualTo(original.second);
        assertThat(read.third).isEqualTo(original.third);
    }

    private static Kryo newKryo() {
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
        return kryo;
    }

    private static void enableMemoryRegions(FieldSerializer<?> serializer) throws Exception {
        Field useMemRegions = FieldSerializer.class.getDeclaredField("useMemRegions");
        useMemRegions.setAccessible(true);
        useMemRegions.setBoolean(serializer, true);
    }

    private static <T> T roundTrip(Kryo kryo, FieldSerializer<T> serializer, T original, Class<T> type) {
        Output output = new Output(256, -1);
        serializer.write(kryo, output, original);
        kryo.reset();

        Input input = new Input(output.toBytes());
        T read = serializer.read(kryo, input, type);
        kryo.reset();
        return read;
    }

    private static String[] fieldNames(FieldSerializer<FieldSerializerSubject> serializer) {
        return Arrays.stream(serializer.getFields())
                .map(FieldSerializer.CachedField::toString)
                .toArray(String[]::new);
    }

    public static class FieldSerializerBase {
        public long createdAt;
        public transient String memo;

        public FieldSerializerBase() {
        }
    }

    public static class FieldSerializerSubject extends FieldSerializerBase {
        public int id;
        public String name;
        public boolean active;
        public double score;

        public FieldSerializerSubject() {
        }

        static FieldSerializerSubject createSample() {
            FieldSerializerSubject value = new FieldSerializerSubject();
            value.id = 42;
            value.name = "field-serializer-subject";
            value.active = true;
            value.score = 98.25d;
            value.createdAt = 123456789L;
            value.memo = "copy-only";
            return value;
        }
    }

    public static class FieldSerializerSubjectConstructorAccess
            extends ConstructorAccess<FieldSerializerSubject> {
        @Override
        public FieldSerializerSubject newInstance() {
            return new FieldSerializerSubject();
        }

        @Override
        public FieldSerializerSubject newInstance(Object enclosingInstance) {
            return newInstance();
        }
    }

    public static class PrimitiveRegionSubject {
        public int first;
        public long second;
        public double third;

        public PrimitiveRegionSubject() {
        }

        static PrimitiveRegionSubject createSample() {
            PrimitiveRegionSubject value = new PrimitiveRegionSubject();
            value.first = 7;
            value.second = 987654321L;
            value.third = 12.5d;
            return value;
        }
    }

    public static class PrimitiveRegionSubjectConstructorAccess
            extends ConstructorAccess<PrimitiveRegionSubject> {
        @Override
        public PrimitiveRegionSubject newInstance() {
            return new PrimitiveRegionSubject();
        }

        @Override
        public PrimitiveRegionSubject newInstance(Object enclosingInstance) {
            return newInstance();
        }
    }
}
