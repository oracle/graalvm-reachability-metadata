/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.EnumMultiset;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class EnumMultisetTest {
    @Test
    void serializationRoundTripPreservesEnumCountsAndType() throws Exception {
        EnumMultiset<Medal> multiset = EnumMultiset.create(Medal.class);
        multiset.add(Medal.GOLD, 2);
        multiset.add(Medal.BRONZE, 3);

        EnumMultiset<Medal> restored = roundTrip(multiset);
        restored.add(Medal.SILVER, 4);

        assertThat(restored.count(Medal.GOLD)).isEqualTo(2);
        assertThat(restored.count(Medal.BRONZE)).isEqualTo(3);
        assertThat(restored.count(Medal.SILVER)).isEqualTo(4);
        assertThat(restored.size()).isEqualTo(9);
        assertThat(restored.elementSet())
                .containsExactlyInAnyOrder(Medal.GOLD, Medal.SILVER, Medal.BRONZE);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> EnumMultiset<E> roundTrip(
            EnumMultiset<E> multiset) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(multiset);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (EnumMultiset<E>) input.readObject();
        }
    }

    private enum Medal {
        GOLD,
        SILVER,
        BRONZE
    }
}
