/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.sundr.deps.org.apache.commons.collections.CursorableLinkedList;
import io.sundr.deps.org.apache.commons.lang.SerializationUtils;
import java.io.Serializable;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CursorableLinkedListTest {
    @Test
    void exercisesPublicApi() throws Exception {
        List original = new CursorableLinkedList();
        original.addAll(List.of("first", "second"));
        assertArrayEquals(new String[]{"first", "second"}, original.toArray(new String[0]));
        List restored = (List) SerializationUtils.clone((Serializable) original);
        assertEquals(List.of("first", "second"), restored);
        restored.add("third");
        assertEquals(2, original.size());
        assertEquals(3, restored.size());
    }
}
