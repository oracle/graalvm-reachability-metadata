/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class SerializerFactoryTest {
    @Test
    void resolvesWireTypeAndRestoresApplicationObject() throws Exception {
        Customer original = new Customer("A-19", "Linden");
        Customer restored = HessianStreams.roundTrip(original);
        assertNotSame(original, restored);
        assertEquals("A-19", restored.id);
        assertEquals("Linden", restored.name);
    }

    public static class Customer implements Serializable {
        private String id;
        private String name;

        public Customer(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
