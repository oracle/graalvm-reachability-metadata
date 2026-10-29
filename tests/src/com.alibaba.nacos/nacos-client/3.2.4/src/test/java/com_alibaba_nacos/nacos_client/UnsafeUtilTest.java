/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.Metadata;
import com.alibaba.nacos.shaded.com.google.protobuf.Any;
import com.alibaba.nacos.shaded.com.google.protobuf.ByteString;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

public class UnsafeUtilTest {
    @Test
    void parsesTransportPayloadsFromDirectBuffers() throws Exception {
        Payload payload = Payload.newBuilder().setMetadata(Metadata.newBuilder().setType("ConfigQueryRequest"))
                .setBody(Any.newBuilder().setValue(ByteString.copyFromUtf8("payload"))).build();
        byte[] encoded = payload.toByteArray();
        ByteBuffer buffer = ByteBuffer.allocateDirect(encoded.length);
        buffer.put(encoded).flip();
        assertEquals(payload, Payload.parseFrom(buffer));
    }
}
