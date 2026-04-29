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
import com.esotericsoftware.kryo.util.IntArray;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldSerializerTest {
    @Test
    void serializesAndCopiesFieldsFromRegisteredLibraryType() {
        Kryo kryo = new Kryo();
        FieldSerializer<IntArray> serializer = new FieldSerializer<>(kryo, IntArray.class);
        kryo.register(IntArray.class, serializer);

        IntArray original = new IntArray(false, new int[] {7, 11, 42});
        Output output = new Output(128, -1);
        kryo.writeObject(output, original);

        IntArray restored = kryo.readObject(new Input(output.toBytes()), IntArray.class);
        IntArray copied = kryo.copy(original);

        assertThat(serializer.getFields()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(restored.ordered).isFalse();
        assertThat(restored.size).isEqualTo(original.size);
        assertThat(values(restored)).containsExactly(7, 11, 42);
        assertThat(copied).isNotSameAs(original);
        assertThat(copied.ordered).isFalse();
        assertThat(values(copied)).containsExactly(7, 11, 42);
    }

    @Test
    void rebuildsCachedFieldsWhenSerializerConfigurationChanges() {
        Kryo kryo = new Kryo();
        FieldSerializer<IntArray> serializer = new FieldSerializer<>(kryo, IntArray.class);
        kryo.register(IntArray.class, serializer);

        serializer.setFieldsCanBeNull(false);
        serializer.setFixedFieldTypes(true);
        serializer.setIgnoreSyntheticFields(false);

        IntArray original = new IntArray(true, new int[] {3, 1, 4, 1, 5});
        Output output = new Output(128, -1);
        kryo.writeObject(output, original);

        IntArray restored = kryo.readObject(new Input(output.toBytes()), IntArray.class);

        assertThat(restored.ordered).isTrue();
        assertThat(restored.size).isEqualTo(original.size);
        assertThat(values(restored)).containsExactly(3, 1, 4, 1, 5);
    }

    @Test
    void sortsFieldsByUnsafeOffsetsWhenMemoryRegionsAreEnabled() throws Exception {
        Kryo kryo = new Kryo();
        FieldSerializer<IntArray> serializer = new FieldSerializer<>(kryo, IntArray.class);
        kryo.register(IntArray.class, serializer);

        enableMemoryRegions(serializer);
        serializer.setFieldsCanBeNull(false);

        IntArray original = new IntArray(true, new int[] {1, 2, 3, 5, 8});
        Output output = new Output(128, -1);
        kryo.writeObject(output, original);

        IntArray restored = kryo.readObject(new Input(output.toBytes()), IntArray.class);

        assertThat(serializer.getUseMemRegions()).isTrue();
        assertThat(restored.ordered).isTrue();
        assertThat(restored.size).isEqualTo(original.size);
        assertThat(values(restored)).containsExactly(1, 2, 3, 5, 8);
    }

    private static void enableMemoryRegions(FieldSerializer<?> serializer) throws Exception {
        Field useMemRegions = FieldSerializer.class.getDeclaredField("useMemRegions");
        useMemRegions.setAccessible(true);
        useMemRegions.set(serializer, true);
    }

    private static int[] values(IntArray array) {
        return Arrays.copyOf(array.items, array.size);
    }
}
