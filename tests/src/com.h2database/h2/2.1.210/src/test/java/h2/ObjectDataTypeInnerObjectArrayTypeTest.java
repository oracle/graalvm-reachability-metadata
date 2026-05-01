/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import java.nio.ByteBuffer;

import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.ObjectDataType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectDataTypeInnerObjectArrayTypeTest {
    @Test
    void readsObjectArrayWithNonCommonComponentType() {
        ObjectDataType dataType = new ObjectDataType();
        CharSequence[] values = { "first", "second" };

        ByteBuffer serialized = serialize(dataType, values);
        Object deserialized = dataType.read(serialized);

        assertThat(deserialized).isInstanceOf(CharSequence[].class);
        assertThat((CharSequence[]) deserialized).containsExactly(values);
    }

    private static ByteBuffer serialize(ObjectDataType dataType, Object value) {
        WriteBuffer writeBuffer = new WriteBuffer(64);
        dataType.write(writeBuffer, value);
        ByteBuffer byteBuffer = writeBuffer.getBuffer();
        byteBuffer.flip();
        return byteBuffer;
    }
}
