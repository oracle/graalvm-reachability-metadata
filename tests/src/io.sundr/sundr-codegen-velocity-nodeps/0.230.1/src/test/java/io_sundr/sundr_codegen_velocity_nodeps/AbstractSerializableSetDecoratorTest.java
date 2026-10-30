/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.sundr.deps.org.apache.commons.collections.set.ListOrderedSet;
import io.sundr.deps.org.apache.commons.lang.SerializationUtils;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class AbstractSerializableSetDecoratorTest {
    @Test
    void exercisesPublicApi() throws Exception {
        ListOrderedSet original = new ListOrderedSet();
        original.add("first");
        original.add("second");
        ListOrderedSet restored = (ListOrderedSet) SerializationUtils.clone(original);
        assertEquals(Set.of("first", "second"), restored);
        assertEquals(List.of("first", "second"), restored.asList());
        assertFalse(restored.add("first"));
    }
}
