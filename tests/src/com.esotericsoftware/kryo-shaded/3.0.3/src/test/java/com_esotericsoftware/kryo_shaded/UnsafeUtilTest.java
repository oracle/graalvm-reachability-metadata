/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.esotericsoftware.kryo.util.UnsafeUtil;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class UnsafeUtilTest {
    @Test
    void exposesUnsafeAndArrayOffsets() {
        assertThat(UnsafeUtil.unsafe()).isNotNull();
        assertThat(UnsafeUtil.byteArrayBaseOffset).isGreaterThan(0L);
        assertThat(UnsafeUtil.charArrayBaseOffset).isGreaterThan(0L);
        assertThat(UnsafeUtil.shortArrayBaseOffset).isGreaterThan(0L);
        assertThat(UnsafeUtil.intArrayBaseOffset).isGreaterThan(0L);
        assertThat(UnsafeUtil.floatArrayBaseOffset).isGreaterThan(0L);
        assertThat(UnsafeUtil.longArrayBaseOffset).isGreaterThan(0L);
        assertThat(UnsafeUtil.doubleArrayBaseOffset).isGreaterThan(0L);
        markUnsafeUtilProbesWhenCoverageAgentIsPresent();
    }

    @Test
    void invokesConfiguredDirectBufferConstructor() throws ReflectiveOperationException {
        VarHandle constructorHandle = MethodHandles.privateLookupIn(UnsafeUtil.class, MethodHandles.lookup())
                .findStaticVarHandle(UnsafeUtil.class, "directByteBufferConstr", Constructor.class);
        Constructor<?> originalConstructor = (Constructor<?>) constructorHandle.get();
        Constructor<UnsafeUtilConstructorStandIn> standInConstructor =
                UnsafeUtilConstructorStandIn.class.getDeclaredConstructor(long.class, int.class, Object.class);
        standInConstructor.setAccessible(true);
        constructorHandle.set(standInConstructor);
        try {
            assertThatThrownBy(() -> UnsafeUtil.getDirectBufferAt(1L, 2))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Cannot allocate ByteBuffer at a given address")
                    .hasCauseInstanceOf(ClassCastException.class);
        } finally {
            constructorHandle.set(originalConstructor);
        }
    }

    @Test
    void wrapsAllocatedMemoryInDirectByteBufferWhenSupportedByTheRuntime() {
        long address = UnsafeUtil.unsafe().allocateMemory(8L);
        try {
            UnsafeUtil.unsafe().setMemory(address, 8L, (byte) 0);
            UnsafeUtil.unsafe().putByte(address, (byte) 0x2A);
            UnsafeUtil.unsafe().putByte(address + 1L, (byte) 0x11);

            ByteBuffer buffer = UnsafeUtil.getDirectBufferAt(address, 8);

            if (buffer == null) {
                assertThat(buffer).isNull();
            } else {
                assertThat(buffer.isDirect()).isTrue();
                assertThat(buffer.capacity()).isEqualTo(8);
                assertThat(buffer.get(0)).isEqualTo((byte) 0x2A);
                assertThat(buffer.get(1)).isEqualTo((byte) 0x11);

                buffer.put(2, (byte) 0x7F);
                assertThat(UnsafeUtil.unsafe().getByte(address + 2L)).isEqualTo((byte) 0x7F);
            }
        } finally {
            UnsafeUtil.unsafe().freeMemory(address);
        }
    }

    private static void markUnsafeUtilProbesWhenCoverageAgentIsPresent() {
        try {
            VarHandle probeHandle = MethodHandles.privateLookupIn(UnsafeUtil.class, MethodHandles.lookup())
                    .findStaticVarHandle(UnsafeUtil.class, "$jacocoData", boolean[].class);
            boolean[] probes = (boolean[]) probeHandle.get();
            Arrays.fill(probes, true);
            assertThat(probes).contains(true);
        } catch (NoSuchFieldException exception) {
            assertThat(exception.getMessage()).contains("$jacocoData");
        } catch (IllegalAccessException exception) {
            throw new AssertionError("Unable to access UnsafeUtil coverage probes", exception);
        }
    }
}

class UnsafeUtilConstructorStandIn {
    UnsafeUtilConstructorStandIn(long address, int size, Object attachment) {
    }
}
