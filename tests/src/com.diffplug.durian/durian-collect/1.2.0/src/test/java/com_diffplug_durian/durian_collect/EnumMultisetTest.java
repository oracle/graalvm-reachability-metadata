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

import com.diffplug.common.collect.EnumMultiset;

public class EnumMultisetTest {
    @Test
    void serializesEnumTypeAndElementCounts() throws Exception {
        EnumMultiset<DurianEnumMultisetElement> original = EnumMultiset.create(DurianEnumMultisetElement.class);
        original.add(DurianEnumMultisetElement.ALPHA, 2);
        original.add(DurianEnumMultisetElement.BETA, 3);

        EnumMultiset<DurianEnumMultisetElement> copy = roundTrip(original);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.count(DurianEnumMultisetElement.ALPHA)).isEqualTo(2);
        assertThat(copy.count(DurianEnumMultisetElement.BETA)).isEqualTo(3);
        assertThat(copy.count(DurianEnumMultisetElement.GAMMA)).isZero();
        assertThat(copy.elementSet()).containsExactly(DurianEnumMultisetElement.ALPHA, DurianEnumMultisetElement.BETA);
    }

    private static EnumMultiset<DurianEnumMultisetElement> roundTrip(
            EnumMultiset<DurianEnumMultisetElement> original)
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

        assertThat(copy).isInstanceOf(EnumMultiset.class);
        @SuppressWarnings("unchecked")
        EnumMultiset<DurianEnumMultisetElement> typedCopy = (EnumMultiset<DurianEnumMultisetElement>) copy;
        return typedCopy;
    }
}

enum DurianEnumMultisetElement {
    ALPHA,
    BETA,
    GAMMA
}
