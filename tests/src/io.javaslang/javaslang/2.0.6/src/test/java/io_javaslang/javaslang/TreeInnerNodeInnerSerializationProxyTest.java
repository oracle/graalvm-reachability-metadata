/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_javaslang.javaslang;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import javaslang.collection.Tree;
import org.junit.jupiter.api.Test;

public class TreeInnerNodeInnerSerializationProxyTest {

    @Test
    void roundTripsNodeWithChildrenThroughSerializationProxy() throws Exception {
        final Tree.Node<String> source = Tree.of(
                "root",
                Tree.of("left"),
                Tree.of("right", Tree.of("right-left")));

        final Object restored = deserialize(serialize(source));

        assertThat(restored).isInstanceOf(Tree.Node.class).isEqualTo(source).isNotSameAs(source);
        final Tree.Node<?> restoredNode = (Tree.Node<?>) restored;
        assertThat(restoredNode.getValue()).isEqualTo("root");
        assertThat(restoredNode.getChildren()).hasSize(2);
        assertThat(restoredNode.getChildren().get(0).getValue()).isEqualTo("left");
        assertThat(restoredNode.getChildren().get(1).getValue()).isEqualTo("right");
        assertThat(restoredNode.getChildren().get(1).getChildren().get(0).getValue())
                .isEqualTo("right-left");
    }

    private static byte[] serialize(final Serializable value) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }

    private static Object deserialize(final byte[] bytes)
            throws IOException, ClassNotFoundException {
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(inputStream)) {
            return objectInputStream.readObject();
        }
    }
}
