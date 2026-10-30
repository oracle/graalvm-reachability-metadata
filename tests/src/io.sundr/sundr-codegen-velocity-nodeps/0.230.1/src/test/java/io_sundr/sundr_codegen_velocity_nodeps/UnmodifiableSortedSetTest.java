/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.sundr.deps.org.apache.commons.collections.set.UnmodifiableSortedSet;
import io.sundr.deps.org.apache.commons.lang.SerializationUtils;
import java.io.Serializable;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

public class UnmodifiableSortedSetTest {
    @Test
    void exercisesPublicApi() throws Exception {
        SortedSet original = UnmodifiableSortedSet.decorate(new TreeSet<>(List.of("pear", "apple")));
        SortedSet restored = (SortedSet) SerializationUtils.clone((Serializable) original);
        assertEquals("apple", restored.first());
        assertEquals("pear", restored.last());
        assertThrows(UnsupportedOperationException.class, () -> restored.add("plum"));
    }
}
