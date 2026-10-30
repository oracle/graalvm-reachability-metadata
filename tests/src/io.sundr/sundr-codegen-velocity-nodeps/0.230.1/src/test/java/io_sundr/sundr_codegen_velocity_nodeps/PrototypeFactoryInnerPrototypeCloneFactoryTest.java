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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class PrototypeFactoryInnerPrototypeCloneFactoryTest {
    @Test
    void exercisesPublicApi() throws Exception {
        ArrayList<String> original = new ArrayList<>(List.of("seed"));
        Factory factory = PrototypeFactory.getInstance(original);
        ArrayList copy = (ArrayList) factory.create();
        assertEquals(original, copy);
        assertNotSame(original, copy);
        copy.add("new");
        assertEquals(List.of("seed"), original);
    }
}
