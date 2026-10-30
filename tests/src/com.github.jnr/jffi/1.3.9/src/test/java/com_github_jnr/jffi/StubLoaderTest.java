/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jffi;

import com.kenai.jffi.Function;
import com.kenai.jffi.HeapInvocationBuffer;
import com.kenai.jffi.Invoker;
import com.kenai.jffi.Library;
import com.kenai.jffi.MemoryIO;
import com.kenai.jffi.Platform;
import com.kenai.jffi.Type;
import org.junit.jupiter.api.Test;

import java.lang.ref.Reference;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class StubLoaderTest {
    @Test
    void loadsNativeStubsForMemoryAndStringCalls() {
        MemoryIO memory = MemoryIO.getCheckedInstance();
        Library library = NativeLibrarySupport.standardLibrary();
        Type sizeType = Platform.getPlatform().addressSize() == 64 ? Type.UINT64 : Type.UINT32;
        Function strlen = new Function(NativeLibrarySupport.symbol(library, "strlen"), sizeType, Type.POINTER);
        byte[] text = "jffi native string".getBytes(StandardCharsets.UTF_8);
        long address = memory.allocateMemory(text.length + 1, true);
        assertNotEquals(0L, address);
        try {
            memory.putZeroTerminatedByteArray(address, text, 0, text.length);
            HeapInvocationBuffer arguments = new HeapInvocationBuffer(strlen);
            arguments.putAddress(address);
            long length = sizeType.size() == 8
                    ? Invoker.getInstance().invokeLong(strlen, arguments)
                    : Invoker.getInstance().invokeInt(strlen, arguments);
            assertEquals(text.length, length);
            assertArrayEquals(text, memory.getZeroTerminatedByteArray(address));
            assertEquals(0, memory.getByte(address + text.length));
        } finally {
            memory.freeMemory(address);
            Reference.reachabilityFence(strlen);
            Reference.reachabilityFence(library);
        }
    }
}
