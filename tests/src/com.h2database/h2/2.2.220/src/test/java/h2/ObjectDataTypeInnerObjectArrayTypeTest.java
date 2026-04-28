/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import java.nio.ByteBuffer;

import org.h2.api.ErrorCode;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.ObjectDataType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectDataTypeInnerObjectArrayTypeTest {
    @Test
    void readsArrayWithNonCommonComponentType() {
        ObjectDataType dataType = new ObjectDataType();
        ErrorCode[] values = new ErrorCode[2];
        WriteBuffer writeBuffer = new WriteBuffer(64);

        dataType.write(writeBuffer, values);
        ByteBuffer readBuffer = writeBuffer.getBuffer();
        readBuffer.flip();

        Object read = dataType.read(readBuffer);

        assertThat(read).isInstanceOf(ErrorCode[].class);
        assertThat((ErrorCode[]) read).hasSize(2).containsOnlyNulls();
        assertThat(readBuffer.hasRemaining()).isFalse();
    }
}
