/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.trie.PatriciaTrie;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractPatriciaTrieTest {

    @Test
    void serializesAndDeserializesPatriciaTrieEntries() throws Exception {
        PatriciaTrie<String> original = new PatriciaTrie<>();
        original.put("alpha", "one");
        original.put("alphabet", "two");
        original.put("beta", "three");

        byte[] serialized = serialize(original);
        PatriciaTrie<String> restored = deserializePatriciaTrie(serialized);

        assertThat(restored)
                .hasSize(3)
                .containsEntry("alpha", "one")
                .containsEntry("alphabet", "two")
                .containsEntry("beta", "three");
        assertThat(restored.firstKey()).isEqualTo("alpha");
        assertThat(restored.nextKey("alpha")).isEqualTo("alphabet");
        assertThat(new LinkedHashMap<>(restored.prefixMap("alph"))).containsExactly(
                Map.entry("alpha", "one"),
                Map.entry("alphabet", "two")
        );

        restored.put("gamma", "four");

        assertThat(restored)
                .hasSize(4)
                .containsEntry("gamma", "four");
    }

    private static byte[] serialize(PatriciaTrie<String> trie) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(trie);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static PatriciaTrie<String> deserializePatriciaTrie(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(PatriciaTrie.class);
            return (PatriciaTrie<String>) restored;
        }
    }
}
