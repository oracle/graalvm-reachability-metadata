/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;

import io.sundr.deps.org.apache.commons.collections.Factory;
import io.sundr.deps.org.apache.commons.collections.functors.PrototypeFactory;
import org.junit.jupiter.api.Test;

public class PrototypeFactoryTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Factory factory = PrototypeFactory.getInstance(new CopyValue("seed"));
        CopyValue copy = (CopyValue) factory.create();
        assertEquals("seed", copy.value);
    }

    public static class CopyValue {
        private final String value;

        public CopyValue(String value) {
            this.value = value;
        }

        public CopyValue(CopyValue original) {
            value = original.value;
        }
    }
}
