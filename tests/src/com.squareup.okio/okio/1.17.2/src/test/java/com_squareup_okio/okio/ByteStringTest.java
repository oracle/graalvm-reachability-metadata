/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okio.okio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import okio.ByteString;
import org.junit.jupiter.api.Test;

public class ByteStringTest {

    @Test
    void deserializesSerializedByteString() throws Exception {
        ByteString original = ByteString.encodeUtf8("ByteString serialization preserves data \uD83D\uDE80");

        ByteString restored = deserialize(serialize(original));

        assertThat(restored).isEqualTo(original);
        assertThat(restored.utf8()).isEqualTo(original.utf8());
        assertThat(restored.toByteArray()).containsExactly(original.toByteArray());
    }

    private static byte[] serialize(ByteString value) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }

        return outputStream.toByteArray();
    }

    private static ByteString deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (ByteString) objectInputStream.readObject();
        }
    }
}
