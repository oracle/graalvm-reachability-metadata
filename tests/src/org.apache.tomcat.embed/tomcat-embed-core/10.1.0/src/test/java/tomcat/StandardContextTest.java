/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardWrapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardContextTest {
    @Test
    void createsConfiguredWrapperAndAttachesItsListeners() throws Exception {
        StandardEngine engine = new StandardEngine();
        engine.setName("WrapperTests");
        StandardHost host = new StandardHost();
        host.setName("localhost");
        engine.addChild(host);
        StandardContext context = new StandardContext();
        context.setName("/wrapper-test");
        context.setPath("/wrapper-test");
        host.addChild(context);
        context.setWrapperClass(CustomWrapper.class.getName());
        context.addWrapperLifecycle(WrapperLifecycle.class.getName());
        context.addWrapperListener(WrapperEvents.class.getName());
        Wrapper wrapper = context.createWrapper();
        wrapper.setName("application");
        context.addChild(wrapper);
        assertThat(wrapper).isInstanceOf(CustomWrapper.class);
        WrapperLifecycle lifecycle = (WrapperLifecycle) wrapper.findLifecycleListeners()[0];
        WrapperEvents events = (WrapperEvents) wrapper.findContainerListeners()[0];
        wrapper.addInitParameter("theme", "dark");
        assertThat(wrapper.findInitParameter("theme")).isEqualTo("dark");
        assertThat(events.types).contains("addInitParameter");
        wrapper.fireContainerEvent("configured", "payload");
        assertThat(events.data).isEqualTo("payload");
        wrapper.init();
        try {
            assertThat(lifecycle.events).contains("before_init", "after_init");
        } finally {
            wrapper.destroy();
        }
    }

    public static class CustomWrapper extends StandardWrapper {
    }

    public static class WrapperLifecycle implements LifecycleListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            events.add(event.getType());
        }
    }

    public static class WrapperEvents implements ContainerListener {
        private final List<String> types = new ArrayList<>();
        private Object data;

        @Override
        public void containerEvent(ContainerEvent event) {
            types.add(event.getType());
            data = event.getData();
        }
    }
}
