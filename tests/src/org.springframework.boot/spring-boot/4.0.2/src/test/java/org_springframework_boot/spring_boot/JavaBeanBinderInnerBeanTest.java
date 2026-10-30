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
    void createsAMutableBeanUsingItsDefaultConstructor() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("queue.capacity", "32")));

        QueueSettings settings = binder.bind("queue", QueueSettings.class).get();

        assertThat(settings.getCapacity()).isEqualTo(32);
    }

    public static class QueueSettings {
        private int capacity;

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }
    }
}
