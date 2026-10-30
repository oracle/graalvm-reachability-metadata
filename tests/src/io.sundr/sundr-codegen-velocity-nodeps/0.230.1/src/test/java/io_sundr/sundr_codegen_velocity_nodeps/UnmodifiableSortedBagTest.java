/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.sundr.deps.org.apache.commons.collections.Bag;
import io.sundr.deps.org.apache.commons.collections.bag.TreeBag;
import io.sundr.deps.org.apache.commons.collections.bag.UnmodifiableSortedBag;
import io.sundr.deps.org.apache.commons.lang.SerializationUtils;
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class UnmodifiableSortedBagTest {
    @Test
    void exercisesPublicApi() throws Exception {
        TreeBag original = new TreeBag();
        original.add("pear", 2);
        original.add("apple", 1);
        assertArrayEquals(new String[]{"apple", "pear", "pear"}, original.toArray(new String[0]));
        Bag restored = (Bag) SerializationUtils.clone((Serializable) UnmodifiableSortedBag.decorate(original));
        assertEquals(3, restored.size());
        assertEquals(2, restored.getCount("pear"));
        assertEquals(1, restored.getCount("apple"));
        assertThrows(UnsupportedOperationException.class, () -> restored.add("plum"));
    }
}
