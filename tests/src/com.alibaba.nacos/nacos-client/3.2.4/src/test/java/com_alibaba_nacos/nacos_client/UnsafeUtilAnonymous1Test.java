/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.protobuf.ByteString;
import com.alibaba.nacos.shaded.com.google.protobuf.CodedInputStream;
import com.alibaba.nacos.shaded.com.google.protobuf.CodedOutputStream;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

public class UnsafeUtilAnonymous1Test {
    @Test
    void readsCodedValuesFromDirectMemory() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocateDirect(64);
        CodedOutputStream output = CodedOutputStream.newInstance(buffer);
        output.writeBytes(1, ByteString.copyFromUtf8("registry"));
        output.flush();
        buffer.flip();
        CodedInputStream input = CodedInputStream.newInstance(buffer);
        assertEquals(10, input.readTag());
        assertEquals("registry", input.readBytes().toStringUtf8());
        assertEquals(0, input.readTag());
    }
}
