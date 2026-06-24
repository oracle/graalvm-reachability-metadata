/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.EnumHashBiMap;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.DayOfWeek;

public class EnumHashBiMapTest {
    @Test
    void serializationRoundTripPreservesEnumKeyTypeAndMappings() throws Exception {
        EnumHashBiMap<DayOfWeek, String> map = EnumHashBiMap.create(DayOfWeek.class);
        map.put(DayOfWeek.MONDAY, "workday");
        map.put(DayOfWeek.SATURDAY, "weekend");
        map.put(DayOfWeek.SUNDAY, null);

        EnumHashBiMap<DayOfWeek, String> restored = roundTrip(map);

        assertThat(restored.keyType()).isSameAs(DayOfWeek.class);
        assertThat(restored)
                .containsEntry(DayOfWeek.MONDAY, "workday")
                .containsEntry(DayOfWeek.SATURDAY, "weekend")
                .containsEntry(DayOfWeek.SUNDAY, null)
                .hasSize(3);
        assertThat(restored.inverse())
                .containsEntry("workday", DayOfWeek.MONDAY)
                .containsEntry("weekend", DayOfWeek.SATURDAY)
                .containsEntry(null, DayOfWeek.SUNDAY);
    }

    @SuppressWarnings("unchecked")
    private static EnumHashBiMap<DayOfWeek, String> roundTrip(
            EnumHashBiMap<DayOfWeek, String> map) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            Object restored = input.readObject();
            assertThat(restored).isInstanceOf(EnumHashBiMap.class);
            return (EnumHashBiMap<DayOfWeek, String>) restored;
        }
    }
}
