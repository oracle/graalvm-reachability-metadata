/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jboss_common_core;

import org.jboss.util.property.MethodBoundPropertyListener;
import org.jboss.util.property.PropertyMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MethodBoundPropertyListenerTest {
    @Test
    void convertsPropertyValuesAndInvokesBeanSetterOnBindingAndChange() {
        Worker worker = new Worker();
        PropertyMap properties = new PropertyMap();
        properties.setProperty("pool.size", "4");
        MethodBoundPropertyListener listener =
                new MethodBoundPropertyListener(worker, "pool.size", "size");

        properties.addPropertyListener(listener);
        assertEquals(4, worker.getSize());
        assertEquals(1, worker.getUpdates());
        properties.setProperty("pool.size", "8");
        assertEquals(8, worker.getSize());
        assertEquals(2, worker.getUpdates());

        properties.removePropertyListener(listener);
        properties.setProperty("pool.size", "16");
        assertEquals(8, worker.getSize());
        assertEquals(2, worker.getUpdates());
    }

    @Test
    void invokesSetterWhenPropertyIsAdded() {
        Worker worker = new Worker();
        PropertyMap properties = new PropertyMap();
        properties.addPropertyListener(new MethodBoundPropertyListener(worker, "size"));
        assertEquals(0, worker.getUpdates());

        properties.setProperty("size", "3");
        assertEquals(3, worker.getSize());
        assertEquals(1, worker.getUpdates());
    }

    public static class Worker {
        private int size;
        private int updates;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
            updates++;
        }

        public int getUpdates() {
            return updates;
        }
    }
}
