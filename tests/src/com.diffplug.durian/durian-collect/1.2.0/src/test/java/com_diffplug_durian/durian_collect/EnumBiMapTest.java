/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import com.diffplug.common.collect.EnumBiMap;

public class EnumBiMapTest {
    @Test
    void serializesKeyAndValueEnumTypesWithEntries() throws Exception {
        EnumBiMap<DurianEnumBiMapKey, DurianEnumBiMapValue> original =
                EnumBiMap.create(DurianEnumBiMapKey.class, DurianEnumBiMapValue.class);
        original.put(DurianEnumBiMapKey.FIRST, DurianEnumBiMapValue.ALPHA);
        original.put(DurianEnumBiMapKey.SECOND, DurianEnumBiMapValue.BETA);

        EnumBiMap<DurianEnumBiMapKey, DurianEnumBiMapValue> copy = roundTrip(original);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.keyType()).isEqualTo(DurianEnumBiMapKey.class);
        assertThat(copy.valueType()).isEqualTo(DurianEnumBiMapValue.class);
        assertThat(copy).containsEntry(DurianEnumBiMapKey.FIRST, DurianEnumBiMapValue.ALPHA)
                .containsEntry(DurianEnumBiMapKey.SECOND, DurianEnumBiMapValue.BETA);
        assertThat(copy.inverse()).containsEntry(DurianEnumBiMapValue.ALPHA, DurianEnumBiMapKey.FIRST)
                .containsEntry(DurianEnumBiMapValue.BETA, DurianEnumBiMapKey.SECOND);
    }

    private static EnumBiMap<DurianEnumBiMapKey, DurianEnumBiMapValue> roundTrip(
            EnumBiMap<DurianEnumBiMapKey, DurianEnumBiMapValue> original)
            throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
            output.flush();
            serialized = bytes.toByteArray();
        }

        Object copy;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            copy = input.readObject();
        }

        assertThat(copy).isInstanceOf(EnumBiMap.class);
        @SuppressWarnings("unchecked")
        EnumBiMap<DurianEnumBiMapKey, DurianEnumBiMapValue> typedCopy =
                (EnumBiMap<DurianEnumBiMapKey, DurianEnumBiMapValue>) copy;
        return typedCopy;
    }
}

enum DurianEnumBiMapKey {
    FIRST,
    SECOND
}

enum DurianEnumBiMapValue {
    ALPHA,
    BETA
}
