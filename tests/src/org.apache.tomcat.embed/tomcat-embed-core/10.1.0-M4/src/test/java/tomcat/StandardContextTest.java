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
    void createsConfiguredWrapperAndInstallsListeners() throws Exception {
        StandardContext context = new StandardContext();
        context.setName("/wrapper-test");
        StandardEngine engine = new StandardEngine();
        engine.setName("WrapperTest");
        StandardHost host = new StandardHost();
        host.setName("localhost");
        host.setParent(engine);
        context.setParent(host);
        context.setWrapperClass(StandardWrapper.class.getName());
        context.addWrapperLifecycle(WrapperLifecycleListener.class.getName());
        context.addWrapperListener(WrapperContainerListener.class.getName());
        Wrapper wrapper = context.createWrapper();
        assertThat(wrapper).isInstanceOf(StandardWrapper.class);
        wrapper.setName("catalog");
        wrapper.setParent(context);
        WrapperLifecycleListener lifecycle = (WrapperLifecycleListener) wrapper.findLifecycleListeners()[0];
        WrapperContainerListener container = (WrapperContainerListener) wrapper.findContainerListeners()[0];
        wrapper.addInitParameter("theme", "dark");
        assertThat(wrapper.findInitParameter("theme")).isEqualTo("dark");
        assertThat(container.events).contains("addInitParameter");
        try {
            wrapper.init();
            assertThat(lifecycle.events).contains("before_init", "after_init");
        } finally {
            wrapper.destroy();
        }
    }

    public static class WrapperLifecycleListener implements LifecycleListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            events.add(event.getType());
        }
    }

    public static class WrapperContainerListener implements ContainerListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void containerEvent(ContainerEvent event) {
            events.add(event.getType());
        }
    }
}
