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

import com.diffplug.common.collect.EnumHashBiMap;

public class EnumHashBiMapTest {
    @Test
    void serializesKeyTypeAndArbitraryValuesWithEntries() throws Exception {
        EnumHashBiMap<DurianEnumHashBiMapKey, String> original = EnumHashBiMap.create(DurianEnumHashBiMapKey.class);
        original.put(DurianEnumHashBiMapKey.FIRST, "alpha");
        original.put(DurianEnumHashBiMapKey.SECOND, "beta");
        original.put(DurianEnumHashBiMapKey.THIRD, null);

        EnumHashBiMap<DurianEnumHashBiMapKey, String> copy = roundTrip(original);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.keyType()).isEqualTo(DurianEnumHashBiMapKey.class);
        assertThat(copy).containsEntry(DurianEnumHashBiMapKey.FIRST, "alpha")
                .containsEntry(DurianEnumHashBiMapKey.SECOND, "beta")
                .containsEntry(DurianEnumHashBiMapKey.THIRD, null);
        assertThat(copy.inverse()).containsEntry("alpha", DurianEnumHashBiMapKey.FIRST)
                .containsEntry("beta", DurianEnumHashBiMapKey.SECOND)
                .containsEntry(null, DurianEnumHashBiMapKey.THIRD);
    }

    private static EnumHashBiMap<DurianEnumHashBiMapKey, String> roundTrip(
            EnumHashBiMap<DurianEnumHashBiMapKey, String> original)
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

        assertThat(copy).isInstanceOf(EnumHashBiMap.class);
        @SuppressWarnings("unchecked")
        EnumHashBiMap<DurianEnumHashBiMapKey, String> typedCopy =
                (EnumHashBiMap<DurianEnumHashBiMapKey, String>) copy;
        return typedCopy;
    }
}

enum DurianEnumHashBiMapKey {
    FIRST,
    SECOND,
    THIRD
}
