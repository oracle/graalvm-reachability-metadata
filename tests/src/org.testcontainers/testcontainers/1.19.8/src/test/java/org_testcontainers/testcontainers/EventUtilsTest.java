/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.event.EventUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class EventUtilsTest {
    @Test
    void bindsSelectedEventsToATargetMethod() {
        EventSource source = new EventSource();
        EventTarget target = new EventTarget();
        EventUtils.bindEventsToMethod(target, "record", source, ChangeListener.class, "changed");

        source.emit("updated");

        assertThat(target.value).isEqualTo("updated");
    }

    public interface ChangeListener {
        void changed(String value);
    }

    public static class EventSource {
        private final List<ChangeListener> listeners = new ArrayList<>();

        public void addChangeListener(ChangeListener listener) {
            listeners.add(listener);
        }

        public void emit(String value) {
            listeners.forEach(listener -> listener.changed(value));
        }
    }

    public static class EventTarget {
        private String value;

        public void record(String value) {
            this.value = value;
        }
    }
}
