/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaBeanBinderInnerBeanTest {
    @Test
    void createsANewBeanBeforeBindingItsProperties() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("worker.capacity", "12")));

        Worker worker = binder.bind("worker", Worker.class).get();

        assertThat(worker.getCapacity()).isEqualTo(12);
    }

    public static class Worker {
        private int capacity;

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }
    }
}
