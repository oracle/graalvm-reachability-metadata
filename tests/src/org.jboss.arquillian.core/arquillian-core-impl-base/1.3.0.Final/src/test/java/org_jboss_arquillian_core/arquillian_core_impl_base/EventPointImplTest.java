/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_core.arquillian_core_impl_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.jboss.arquillian.core.api.Event;
import org.jboss.arquillian.core.impl.EventPointImpl;
import org.jboss.arquillian.core.spi.EventPoint;
import org.junit.jupiter.api.Test;

public class EventPointImplTest {
    @Test
    void setInjectsEventIntoTargetField() throws Exception {
        EventTarget target = new EventTarget();
        Field eventField = EventTarget.class.getDeclaredField("event");
        Event<String> event = new NoOpEvent();

        EventPoint eventPoint = EventPointImpl.of(target, eventField);
        eventPoint.set(event);

        assertThat(target.event()).isSameAs(event);
        assertThat(eventPoint.getType()).isEqualTo(String.class);
    }

    private static final class EventTarget {
        private Event<String> event;

        private Event<String> event() {
            return event;
        }
    }

    private static final class NoOpEvent implements Event<String> {
        @Override
        public void fire(String event) {
        }
    }
}
