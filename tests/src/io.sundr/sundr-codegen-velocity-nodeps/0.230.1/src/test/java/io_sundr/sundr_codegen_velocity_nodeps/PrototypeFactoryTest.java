/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.commons.collections.Factory;
import io.sundr.deps.org.apache.commons.collections.functors.PrototypeFactory;
import io.sundr.deps.org.apache.commons.lang.SerializationUtils;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class PrototypeFactoryTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Factory factory = PrototypeFactory.getInstance(new CopyValue("seed"));
        CopyValue copy = (CopyValue) factory.create();
        assertEquals("seed", copy.value);
    }

    @Test
    void rejectsSerializationOfPrototypeFactoriesWithoutExplicitOptIn() {
        Factory factory = PrototypeFactory.getInstance(Integer.valueOf(7));
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> SerializationUtils.serialize((Serializable) factory));
        assertTrue(exception.getMessage().contains("PrototypeSerializationFactory"));
        assertTrue(exception.getMessage().contains("disabled for security reasons"));
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
