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
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class FieldSerializerTest {
    @Test
    void serializesAndCopiesFieldsDeclaredAcrossTheClassHierarchy() {
        Kryo kryo = new Kryo();
        FieldSerializer<FieldSerializerSubject> serializer = new FieldSerializer<>(kryo, FieldSerializerSubject.class);
        FieldSerializerSubject original = FieldSerializerSubject.createSample();

        FieldSerializerSubject read = roundTrip(kryo, serializer, original);
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
        Kryo kryo = new Kryo();
        kryo.setAsmEnabled(false);
        FieldSerializer<FieldSerializerSubject> serializer = new FieldSerializer<>(kryo, FieldSerializerSubject.class);

        serializer.setFieldsCanBeNull(false);
        serializer.setFixedFieldTypes(true);
        serializer.setIgnoreSyntheticFields(true);
        serializer.setUseAsm(false);

        FieldSerializerSubject original = FieldSerializerSubject.createSample();
        FieldSerializerSubject read = roundTrip(kryo, serializer, original);

        assertThat(serializer.getField("name").toString()).isEqualTo("name");
        assertThat(read.name).isEqualTo(original.name);
        assertThat(read.id).isEqualTo(original.id);
        assertThat(read.score).isEqualTo(original.score);
    }

    private static FieldSerializerSubject roundTrip(
            Kryo kryo, FieldSerializer<FieldSerializerSubject> serializer, FieldSerializerSubject original) {
        Output output = new Output(256, -1);
        serializer.write(kryo, output, original);
        kryo.reset();

        Input input = new Input(output.toBytes());
        FieldSerializerSubject read = serializer.read(kryo, input, FieldSerializerSubject.class);
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
}
