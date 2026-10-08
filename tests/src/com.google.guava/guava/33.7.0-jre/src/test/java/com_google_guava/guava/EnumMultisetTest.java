/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.EnumMultiset;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class EnumMultisetTest {
    @Test
    void roundTripSerializesEnumTypeAndCounts() throws Exception {
        EnumMultiset<Signal> multiset = EnumMultiset.create(Signal.class);
        multiset.add(Signal.RED, 2);
        multiset.add(Signal.GREEN, 3);

        EnumMultiset<Signal> restored = roundTrip(multiset);

        assertThat(restored).isEqualTo(multiset);
        assertThat(restored.elementSet()).containsExactly(Signal.RED, Signal.GREEN);
        assertThat(restored.count(Signal.RED)).isEqualTo(2);
        assertThat(restored.count(Signal.GREEN)).isEqualTo(3);
        assertThat(restored.count(Signal.BLUE)).isZero();
    }

    private static EnumMultiset<Signal> roundTrip(EnumMultiset<Signal> multiset)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(multiset);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(EnumMultiset.class);
            @SuppressWarnings("unchecked")
            EnumMultiset<Signal> typedRestored = (EnumMultiset<Signal>) restored;
            return typedRestored;
        }
    }

    private enum Signal {
        RED,
        GREEN,
        BLUE
    }
}
