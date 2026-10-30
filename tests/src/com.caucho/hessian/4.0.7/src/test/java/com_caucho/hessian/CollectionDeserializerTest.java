/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CollectionDeserializerTest {
    @Test
    void restoresConcreteCollectionTypesAndElements() throws Exception {
        for (boolean hessian2 : new boolean[] {false, true}) {
            LinkedList<String> list = new LinkedList<>(List.of("second", "first"));
            LinkedList<String> restored = HessianStreams.roundTrip(list, new SerializerFactory(), hessian2);
            assertEquals(LinkedList.class, restored.getClass());
            assertEquals(list, restored);
            TreeSet<String> sorted = new TreeSet<>(list);
            TreeSet<String> restoredSet = HessianStreams.roundTrip(sorted, new SerializerFactory(), hessian2);
            assertEquals(TreeSet.class, restoredSet.getClass());
            assertEquals("first", restoredSet.first());
            assertEquals(sorted, restoredSet);
        }
    }
}
