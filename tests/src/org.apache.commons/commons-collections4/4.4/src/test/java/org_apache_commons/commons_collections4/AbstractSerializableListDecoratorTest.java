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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.list.UnmodifiableList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbstractSerializableListDecoratorTest {

    @Test
    void serializesAndDeserializesDecoratedListState() throws Exception {
        List<String> original = UnmodifiableList.unmodifiableList(new ArrayList<>(List.of("alpha", "beta", "gamma")));

        assertThat(original).isInstanceOf(UnmodifiableList.class);
        assertThatThrownBy(() -> original.add("delta"))
                .isInstanceOf(UnsupportedOperationException.class);

        byte[] serialized = serialize(original);
        List<String> restored = deserializeUnmodifiableList(serialized);

        assertThat(restored)
                .isInstanceOf(UnmodifiableList.class)
                .containsExactly("alpha", "beta", "gamma");
        assertThat(restored.subList(1, 3))
                .isInstanceOf(UnmodifiableList.class)
                .containsExactly("beta", "gamma");
        assertThatThrownBy(() -> restored.add("delta"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.remove("alpha"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.set(0, "delta"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(List<String> list) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(list);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static List<String> deserializeUnmodifiableList(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(UnmodifiableList.class);
            return (List<String>) restored;
        }
    }
}
