/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.sundr.deps.org.apache.commons.collections.Buffer;
import io.sundr.deps.org.apache.commons.collections.buffer.BoundedFifoBuffer;
import io.sundr.deps.org.apache.commons.collections.buffer.UnmodifiableBuffer;
import io.sundr.deps.org.apache.commons.lang.SerializationUtils;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class UnmodifiableBufferTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Buffer original = new BoundedFifoBuffer(3);
        original.add("first");
        original.add("second");
        Buffer restored = (Buffer) SerializationUtils.clone((Serializable) UnmodifiableBuffer.decorate(original));
        assertEquals(2, restored.size());
        assertEquals("first", restored.get());
        assertThrows(UnsupportedOperationException.class, restored::remove);
    }
}
