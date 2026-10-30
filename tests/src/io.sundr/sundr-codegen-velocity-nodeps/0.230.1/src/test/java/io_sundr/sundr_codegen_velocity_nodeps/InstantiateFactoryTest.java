/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.commons.collections.Factory;
import io.sundr.deps.org.apache.commons.collections.functors.InstantiateFactory;
import io.sundr.deps.org.apache.commons.lang.SerializationUtils;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class InstantiateFactoryTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Factory factory = InstantiateFactory.getInstance(StringBuilder.class, new Class<?>[]{String.class}, new Object[]{"seed"});
        assertEquals("seed", factory.create().toString());
        assertNotSame(factory.create(), factory.create());
    }

    @Test
    void rejectsSerializationWithoutExplicitOptIn() {
        Factory factory = InstantiateFactory.getInstance(StringBuilder.class, null, null);
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> SerializationUtils.serialize((Serializable) factory));
        assertTrue(exception.getMessage().contains(InstantiateFactory.class.getName()));
        assertTrue(exception.getMessage().contains("disabled for security reasons"));
    }
}
