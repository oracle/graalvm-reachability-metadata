/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardWrapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class StandardContextTest {
    @Test
    void createsConfiguredWrapperAndItsListeners() throws Exception {
        StandardContext context = new StandardContext();
        context.setName("/application");
        context.setPath("/application");
        StandardHost host = new StandardHost();
        host.setName("localhost");
        host.addChild(context);
        context.setWrapperClass(ApplicationWrapper.class.getName());
        context.addWrapperLifecycle(WrapperLifecycle.class.getName());
        context.addWrapperListener(WrapperEvents.class.getName());
        Wrapper wrapper = context.createWrapper();
        assertInstanceOf(ApplicationWrapper.class, wrapper);
        wrapper.setName("servlet");
        context.addChild(wrapper);
        WrapperLifecycle lifecycle = (WrapperLifecycle) wrapper.findLifecycleListeners()[0];
        WrapperEvents events = (WrapperEvents) wrapper.findContainerListeners()[0];
        try {
            wrapper.init();
            wrapper.fireContainerEvent("configured", "payload");
            assertEquals("payload", events.payload);
            assertEquals("after_init", lifecycle.lastEvent);
        } finally {
            wrapper.destroy();
        }
    }

    public static class ApplicationWrapper extends StandardWrapper {
    }

    public static class WrapperLifecycle implements LifecycleListener {
        private String lastEvent;

        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            lastEvent = event.getType();
        }
    }

    public static class WrapperEvents implements ContainerListener {
        private Object payload;

        @Override
        public void containerEvent(ContainerEvent event) {
            payload = event.getData();
        }
    }
}
