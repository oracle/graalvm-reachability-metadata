/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Timeout(120)
public class CollectionDeserializerTest {
    @Test
    void restoresConcreteCollection() throws Exception {
        LinkedList<String> values = new LinkedList<>(List.of("leader", "follower"));
        Object restored = SerializationSupport.roundTrip(values);
        assertInstanceOf(LinkedList.class, restored);
        assertEquals(values, restored);
    }
}
