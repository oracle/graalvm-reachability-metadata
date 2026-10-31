/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.protobuf.CodedInputStream;
import com.couchbase.client.core.deps.com.google.protobuf.CodedOutputStream;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProtobufUnsafeUtilTest {
    @Test
    void exercisesPublicApi() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocateDirect(128);
        CodedOutputStream output = CodedOutputStream.newInstance(buffer);
        output.writeString(1, "payload");
        output.writeInt64(2, 123456789L);
        output.flush();
        buffer.flip();
        CodedInputStream input = CodedInputStream.newInstance(buffer);
        assertEquals(10, input.readTag());
        assertEquals("payload", input.readStringRequireUtf8());
        assertEquals(16, input.readTag());
        assertEquals(123456789L, input.readInt64());
        assertEquals(0, input.readTag());
    }
}
