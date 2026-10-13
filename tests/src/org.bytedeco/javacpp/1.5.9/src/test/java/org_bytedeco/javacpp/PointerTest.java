/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.javacpp;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;

import javax.management.ObjectName;

import org.junit.jupiter.api.Test;

import org.bytedeco.javacpp.Pointer;

public class PointerTest {

    @Test
    public void initializationRegistersPointerBufferPoolMxBean() throws Exception {
        Pointer.maxBytes();

        ObjectName pointerPool = new ObjectName("java.nio:type=BufferPool,name=javacpp");

        assertThat(ManagementFactory.getPlatformMBeanServer().isRegistered(pointerPool)).isTrue();
    }

    @Test
    public void getPointerConstructsAndOffsetsRequestedPointerType() {
        Pointer source = new Pointer().capacity(10).limit(8).position(2);

        Pointer offset = source.getPointer(Pointer.class, 3);

        assertThat(offset).isNotSameAs(source);
        assertThat(offset.address()).isEqualTo(3);
        assertThat(offset.position()).isEqualTo(2);
        assertThat(offset.limit()).isEqualTo(5);
        assertThat(offset.capacity()).isEqualTo(7);
    }
}
