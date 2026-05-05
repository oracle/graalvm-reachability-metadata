/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.google.common.collect.EnumBiMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumBiMapTest {
    @Test
    void serializesEnumKeyAndValueTypesWithEntries() throws Exception {
        EnumBiMap<SourceState, TargetState> original = EnumBiMap.create(SourceState.class, TargetState.class);
        original.put(SourceState.CREATED, TargetState.OPEN);
        original.put(SourceState.CLOSED, TargetState.DONE);

        EnumBiMap<SourceState, TargetState> copy = roundTrip(original);

        assertThat(copy).isEqualTo(original);
        assertThat(copy.keyType()).isSameAs(SourceState.class);
        assertThat(copy.valueType()).isSameAs(TargetState.class);
        assertThat(copy.inverse().get(TargetState.OPEN)).isEqualTo(SourceState.CREATED);
        assertThat(copy.inverse().get(TargetState.DONE)).isEqualTo(SourceState.CLOSED);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream input = new ObjectInputStream(inputBytes)) {
            return (T) input.readObject();
        }
    }

    private enum SourceState {
        CREATED,
        CLOSED
    }

    private enum TargetState {
        OPEN,
        DONE
    }
}
