/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.list.NodeCachingLinkedList;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractLinkedListTest {

    @Test
    void growsTypedArrayWhenTargetArrayIsTooSmall() {
        NodeCachingLinkedList<String> list = new NodeCachingLinkedList<>();
        list.add("alpha");
        list.add("beta");

        String[] restored = list.toArray(new String[0]);

        assertThat(restored)
                .isInstanceOf(String[].class)
                .containsExactly("alpha", "beta");
    }

    @Test
    void serializesAndDeserializesListElements() throws Exception {
        NodeCachingLinkedList<String> original = new NodeCachingLinkedList<>();
        original.add("alpha");
        original.add("beta");

        byte[] serialized = serialize(original);
        NodeCachingLinkedList<String> restored = deserializeNodeCachingLinkedList(serialized);

        assertThat(restored).containsExactly("alpha", "beta");

        restored.add("gamma");

        assertThat(restored).containsExactly("alpha", "beta", "gamma");
    }

    private static byte[] serialize(NodeCachingLinkedList<String> list) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(list);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static NodeCachingLinkedList<String> deserializeNodeCachingLinkedList(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(NodeCachingLinkedList.class);
            return (NodeCachingLinkedList<String>) restored;
        }
    }
}
