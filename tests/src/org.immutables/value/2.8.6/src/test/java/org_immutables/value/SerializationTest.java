/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_immutables.value;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.immutables.value.Value;
import org.immutables.value.internal.$guava$.collect.$ArrayListMultimap;
import org.immutables.value.internal.$guava$.collect.$HashMultiset;
import org.immutables.value.internal.$guava$.collect.$ImmutableSetMultimap;
import org.immutables.value.internal.$guava$.collect.$MapMaker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializationTest {

    @Test
    void serializableImmutableWithMapAttributePreservesEntriesAcrossRoundTrip() throws Exception {
        ImmutableSerializableScoreboard original = ImmutableSerializableScoreboard.builder()
                .putScores("wins", 12)
                .putScores("losses", 2)
                .build();

        ImmutableSerializableScoreboard restored = roundTrip(original, ImmutableSerializableScoreboard.class);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.scores()).containsExactlyInAnyOrderEntriesOf(original.scores());
    }

    @Test
    void mapMakerWeakKeyMapPreservesEntriesAcrossRoundTripWhenKeysRemainStronglyReachable() throws Exception {
        String alpha = new String("alpha");
        String beta = new String("beta");
        ConcurrentMap<String, Integer> original = new $MapMaker()
                .weakKeys()
                .makeMap();
        original.put(alpha, 1);
        original.put(beta, 2);

        SerializableWeakKeyMapHolder restored = roundTrip(
                new SerializableWeakKeyMapHolder(original, List.of(alpha, beta)),
                SerializableWeakKeyMapHolder.class
        );

        assertThat(restored.keys).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(restored.map).hasSize(2);
        assertThat(restored.map.values()).containsExactlyInAnyOrder(1, 2);
        assertThat(restored.map.keySet()).containsExactlyInAnyOrderElementsOf(restored.keys);
    }

    @Test
    void mutableArrayListMultimapPreservesKeysAndValueOrderAcrossRoundTrip() throws Exception {
        $ArrayListMultimap<String, String> original = $ArrayListMultimap.create();
        original.put("team", "ada");
        original.put("team", "grace");
        original.put("language", "java");

        $ArrayListMultimap<String, String> restored = roundTrip(original, $ArrayListMultimap.class);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.get("team")).containsExactly("ada", "grace");
        assertThat(restored.get("language")).containsExactly("java");
    }

    @Test
    void mutableHashMultisetPreservesDistinctElementCountsAcrossRoundTrip() throws Exception {
        $HashMultiset<String> original = $HashMultiset.create();
        original.add("alpha", 3);
        original.add("beta", 1);

        $HashMultiset<String> restored = roundTrip(original, $HashMultiset.class);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.count("alpha")).isEqualTo(3);
        assertThat(restored.count("beta")).isEqualTo(1);
        assertThat(restored.elementSet()).containsExactlyInAnyOrder("alpha", "beta");
    }

    @Test
    void immutableSetMultimapRebuildsInverseStateAcrossRoundTrip() throws Exception {
        $ImmutableSetMultimap<String, String> original = $ImmutableSetMultimap.<String, String>builder()
                .put("team", "ada")
                .put("team", "grace")
                .put("language", "java")
                .build();

        $ImmutableSetMultimap<String, String> restored = roundTrip(original, $ImmutableSetMultimap.class);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.get("team")).containsExactlyInAnyOrder("ada", "grace");
        assertThat(restored.inverse().get("ada")).containsExactly("team");
    }

    private static <T> T roundTrip(Serializable value, Class<T> expectedType) throws IOException, ClassNotFoundException {
        byte[] serialized = serialize(value);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(expectedType);
            return expectedType.cast(restored);
        }
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }

    private static final class SerializableWeakKeyMapHolder implements Serializable {
        private static final long serialVersionUID = 1L;

        final ConcurrentMap<String, Integer> map;
        final List<String> keys;

        private SerializableWeakKeyMapHolder(ConcurrentMap<String, Integer> map, List<String> keys) {
            this.map = map;
            this.keys = keys;
        }
    }
}

@Value.Immutable
interface SerializableScoreboard extends Serializable {
    Map<String, Integer> scores();
}
