/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.sundr.deps.org.apache.commons.collections.list.SetUniqueList;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SetUniqueListTest {
    @Test
    void exercisesPublicApi() throws Exception {
        SetUniqueList list = new OrderedUniqueList();
        list.addAll(List.of("one", "two", "one", "three"));
        assertEquals(List.of("one", "two", "three"), list);
        List subset = list.subList(0, 2);
        assertEquals(List.of("one", "two"), subset);
        assertFalse(subset.add("one"));
        assertEquals(3, list.size());
    }

    public static class OrderedUniqueList extends SetUniqueList {
        public OrderedUniqueList() {
            super(new ArrayList<>(), new LinkedHashSet<>());
        }
    }
}
