/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package biz_aQute_bnd.biz_aQute_bnd_util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import aQute.bnd.unmodifiable.Lists;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

public class ImmutableListInnerSerializationProxyTest {
    @Test
    void serializesAndDeserializesImmutableListThroughSerializationProxy() throws Exception {
        List<String> original = Lists.of("alpha", "beta", "gamma");

        byte[] serialized = serialize(original);
        Object deserialized = deserialize(serialized);

        assertThat(deserialized)
            .isInstanceOf(List.class)
            .isEqualTo(original)
            .isNotSameAs(original);
        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> asStringList(deserialized).add("delta"));
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return input.readObject();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        return (List<String>) value;
    }
}
