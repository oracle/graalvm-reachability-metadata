/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_javaslang.javaslang;

import javaslang.collection.Tree;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeInnerNodeInnerSerializationProxyTest {

    @Test
    void serializesAndDeserializesNodeThroughSerializationProxy() throws Exception {
        final Tree.Node<String> tree = Tree.of(
                "root",
                Tree.of("left", Tree.of("left.leaf")),
                Tree.of("right")
        );

        final Tree.Node<String> roundTripped = deserialize(serialize(tree));

        assertThat(roundTripped).isEqualTo(tree);
        assertThat(roundTripped).isNotSameAs(tree);
        assertThat(roundTripped.draw()).isEqualTo("""
                root
                \u251c\u2500\u2500left
                \u2502  \u2514\u2500\u2500left.leaf
                \u2514\u2500\u2500right""");
    }

    private static byte[] serialize(Tree.Node<String> tree) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(tree);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static Tree.Node<String> deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (Tree.Node<String>) input.readObject();
        }
    }
}
