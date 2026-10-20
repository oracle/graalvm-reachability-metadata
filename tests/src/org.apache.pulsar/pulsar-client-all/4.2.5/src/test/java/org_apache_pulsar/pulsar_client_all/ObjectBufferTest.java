/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.util.ObjectBuffer;
import org.junit.jupiter.api.Test;

public class ObjectBufferTest {
    @Test
    void completesBufferAsTypedArray() {
        ObjectBuffer buffer = new ObjectBuffer();
        Object[] chunk = buffer.resetAndStart();
        chunk[0] = "first";
        chunk[1] = "second";

        String[] values = buffer.completeAndClearBuffer(chunk, 2, String.class);

        assertThat(values).containsExactly("first", "second");
        assertThat(values.getClass()).isEqualTo(String[].class);
        assertThat(buffer.bufferedSize()).isZero();
    }
}
