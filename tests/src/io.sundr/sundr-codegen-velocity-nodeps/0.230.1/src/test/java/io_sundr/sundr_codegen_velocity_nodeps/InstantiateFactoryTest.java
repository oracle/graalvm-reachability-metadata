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
import io.sundr.deps.org.apache.commons.collections.functors.InstantiateFactory;
import org.junit.jupiter.api.Test;

public class InstantiateFactoryTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Factory factory = InstantiateFactory.getInstance(StringBuilder.class, new Class<?>[]{String.class}, new Object[]{"seed"});
        assertEquals("seed", factory.create().toString());
        assertNotSame(factory.create(), factory.create());
    }
}
