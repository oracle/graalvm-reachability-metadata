/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.sundr.deps.org.apache.commons.collections.list.FixedSizeList;
import io.sundr.deps.org.apache.commons.lang.SerializationUtils;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class AbstractSerializableListDecoratorTest {
    @Test
    void exercisesPublicApi() throws Exception {
        List original = FixedSizeList.decorate(new ArrayList<>(List.of("first", "second")));
        List restored = (List) SerializationUtils.clone((Serializable) original);
        assertEquals(List.of("first", "second"), restored);
        assertEquals("first", restored.set(0, "updated"));
        assertThrows(UnsupportedOperationException.class, () -> restored.add("third"));
    }
}
