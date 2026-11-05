/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalactic.scalactic_2_13;

import org.junit.jupiter.api.Test;
import org.scalactic.source.ObjectMeta;
import org.scalactic.source.ObjectMeta$;
import scala.Tuple2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ObjectMetaAnonymous1Test {
    @Test
    void describesTupleFieldsThroughTheirPublicAccessors() {
        Tuple2<String, Integer> tuple = new Tuple2<>("pears", 6);
        ObjectMeta metadata = ObjectMeta$.MODULE$.apply(tuple);

        assertEquals(2, metadata.fieldNames().size());
        assertTrue(metadata.hasField("_1"));
        assertTrue(metadata.hasField("_2"));
        assertFalse(metadata.hasField("missing"));
        assertEquals("pears", metadata.value("_1"));
        assertEquals(6, metadata.value("_2"));
        assertEquals("java.lang.String", metadata.typeName("_1"));
        assertEquals("Integer", metadata.shortTypeName("_2"));
    }

    @Test
    void readsCurrentValuesFromANonPublicUserClass() {
        OrderLine line = new OrderLine("notebook", 3);
        ObjectMeta metadata = ObjectMeta$.MODULE$.apply(line);

        assertEquals(2, metadata.fieldNames().size());
        assertTrue(metadata.hasField("description"));
        assertTrue(metadata.hasField("quantity"));
        assertFalse(metadata.hasField("changeQuantity"));
        assertEquals("notebook", metadata.value("description"));
        assertEquals(3, metadata.value("quantity"));
        assertEquals("java.lang.Integer", metadata.typeName("quantity"));
        assertEquals("String", metadata.shortTypeName("description"));

        line.changeQuantity(8);
        assertEquals(8, metadata.value("quantity"));
    }

    private static final class OrderLine {
        private final String description;
        private int quantity;

        private OrderLine(String description, int quantity) {
            this.description = description;
            this.quantity = quantity;
        }

        public String description() {
            return description;
        }

        public int quantity() {
            return quantity;
        }

        public void changeQuantity(int value) {
            quantity = value;
        }
    }
}
