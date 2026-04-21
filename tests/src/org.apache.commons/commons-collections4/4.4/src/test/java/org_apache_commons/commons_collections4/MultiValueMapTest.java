/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.commons.collections4.map.MultiValueMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class MultiValueMapTest {

    @Test
    void serializesAndDeserializesMultiValueCollectionsAndSupportsFurtherMutations() throws Exception {
        MultiValueMap<String, String> original = new MultiValueMap<>();
        original.put("letters", "alpha");
        original.put("letters", "beta");
        original.put("numbers", "one");

        assertThat(original.values())
                .containsExactlyInAnyOrder("alpha", "beta", "one");

        byte[] serialized = serialize(original);
        MultiValueMap<String, String> restored = deserializeMultiValueMap(serialized);

        assertThat(restored.totalSize()).isEqualTo(3);
        assertThat(restored.keySet())
                .containsExactlyInAnyOrder("letters", "numbers");
        assertThat(restored.getCollection("letters"))
                .containsExactlyInAnyOrder("alpha", "beta");
        assertThat(restored.getCollection("numbers"))
                .containsExactly("one");
        assertThat(restored.containsValue("letters", "alpha")).isTrue();
        assertThat(restored.values())
                .containsExactlyInAnyOrder("alpha", "beta", "one");

        assertThat(restored.removeMapping("letters", "alpha")).isTrue();
        assertThat(restored.removeMapping("letters", "beta")).isTrue();
        restored.put("symbols", "omega");

        assertThat(restored.keySet())
                .containsExactlyInAnyOrder("numbers", "symbols");
        assertThat(restored.totalSize()).isEqualTo(2);
        assertThat(restored.getCollection("letters")).isNull();
        assertThat(restored.getCollection("symbols"))
                .containsExactly("omega");
        assertThat(restored.values())
                .containsExactlyInAnyOrder("one", "omega");
    }

    private static byte[] serialize(MultiValueMap<String, String> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static MultiValueMap<String, String> deserializeMultiValueMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(MultiValueMap.class);
            return (MultiValueMap<String, String>) restored;
        }
    }
}
