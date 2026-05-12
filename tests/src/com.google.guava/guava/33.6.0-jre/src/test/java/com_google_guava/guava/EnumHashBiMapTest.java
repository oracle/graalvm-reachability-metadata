/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.EnumHashBiMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class EnumHashBiMapTest {
    @Test
    void roundTripSerializesEnumKeyTypeAndValues() throws Exception {
        EnumHashBiMap<State, String> map = EnumHashBiMap.create(State.class);
        map.put(State.CREATED, "created");
        map.put(State.RUNNING, "running");
        map.put(State.FINISHED, null);

        EnumHashBiMap<State, String> restored = roundTrip(map);

        assertThat(restored).isEqualTo(map);
        assertThat(restored.keyType()).isSameAs(State.class);
        assertThat(restored.inverse().get("created")).isSameAs(State.CREATED);
        assertThat(restored.inverse().get("running")).isSameAs(State.RUNNING);
        assertThat(restored.inverse().get(null)).isSameAs(State.FINISHED);
    }

    private static EnumHashBiMap<State, String> roundTrip(EnumHashBiMap<State, String> map)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(map);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(EnumHashBiMap.class);
            @SuppressWarnings("unchecked")
            EnumHashBiMap<State, String> typedRestored = (EnumHashBiMap<State, String>) restored;
            return typedRestored;
        }
    }

    private enum State {
        CREATED,
        RUNNING,
        FINISHED
    }
}
