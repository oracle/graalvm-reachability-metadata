/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import io.sundr.deps.org.apache.commons.collections.Factory;
import io.sundr.deps.org.apache.commons.collections.functors.PrototypeFactory;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class PrototypeFactoryInnerPrototypeSerializationFactoryTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Payload original = new Payload("seed");
        Factory factory = PrototypeFactory.getInstance(original);
        Payload copy = (Payload) factory.create();
        assertNotSame(original, copy);
        assertEquals("seed", copy.value);
    }

    public static class Payload implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String value;

        public Payload(String value) {
            this.value = value;
        }
    }
}
