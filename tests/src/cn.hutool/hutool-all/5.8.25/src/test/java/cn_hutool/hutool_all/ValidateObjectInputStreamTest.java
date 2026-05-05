/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.io.ValidateObjectInputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidateObjectInputStreamTest {

    @Test
    void resolvesAllowedClassDuringDeserialization() throws Exception {
        ArrayList<String> values = new ArrayList<>(List.of("hutool", "native-image"));

        Object deserialized = deserialize(serialize(values), ArrayList.class);

        assertThat(deserialized).isEqualTo(values);
    }

    private byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }

    private Object deserialize(byte[] serialized, Class<?>... acceptedClasses) throws Exception {
        try (ValidateObjectInputStream inputStream = new ValidateObjectInputStream(
                new ByteArrayInputStream(serialized), acceptedClasses)) {
            return inputStream.readObject();
        }
    }
}
