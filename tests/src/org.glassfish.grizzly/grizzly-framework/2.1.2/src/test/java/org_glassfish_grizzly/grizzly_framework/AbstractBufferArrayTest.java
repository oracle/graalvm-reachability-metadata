/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_framework;

import org.glassfish.grizzly.memory.ByteBufferArray;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractBufferArrayTest {
    @Test
    void createsTypedArrayAndRestoresBufferState() {
        ByteBufferArray[] arrays = new ByteBufferArray[5];

        try {
            for (int i = 0; i < arrays.length; i++) {
                arrays[i] = ByteBufferArray.create();
            }

            ByteBufferArray array = arrays[arrays.length - 1];
            ByteBuffer first = ByteBuffer.allocate(8);
            first.position(2);
            first.limit(6);
            ByteBuffer second = ByteBuffer.allocate(4);
            second.position(1);
            second.limit(3);

            array.add(first, 0, 8);
            array.add(second);

            assertThat(array.size()).isEqualTo(2);
            assertThat(array.getArray()).isInstanceOf(ByteBuffer[].class);
            assertThat(array.getInitialPosition(0)).isEqualTo(2);
            assertThat(array.getInitialLimit(0)).isEqualTo(6);
            assertThat(array.getInitialBufferSize(0)).isEqualTo(4);
            assertThat(array.getInitialPosition(1)).isEqualTo(1);
            assertThat(array.getInitialLimit(1)).isEqualTo(3);

            first.position(4);
            first.limit(5);
            second.position(2);
            second.limit(4);

            array.restore();

            assertThat(first.position()).isZero();
            assertThat(first.limit()).isEqualTo(8);
            assertThat(second.position()).isEqualTo(1);
            assertThat(second.limit()).isEqualTo(3);
        } finally {
            for (ByteBufferArray array : arrays) {
                if (array != null) {
                    array.recycle();
                }
            }
        }
    }
}
