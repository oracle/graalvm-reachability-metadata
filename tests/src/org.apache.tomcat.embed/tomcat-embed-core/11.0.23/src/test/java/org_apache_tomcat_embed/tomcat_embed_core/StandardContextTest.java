/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardWrapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardContextTest {

    @Test
    void createsConfiguredWrapperWithListeners() {
        StandardContext context = new StandardContext();
        context.setWrapperClass(TestWrapper.class.getName());
        context.addWrapperLifecycle(TestLifecycleListener.class.getName());
        context.addWrapperListener(TestContainerListener.class.getName());

        Wrapper wrapper = context.createWrapper();

        assertThat(wrapper).isInstanceOf(TestWrapper.class);
        assertThat(wrapper.findLifecycleListeners()).anyMatch(TestLifecycleListener.class::isInstance);
        assertThat(wrapper.findContainerListeners()).anyMatch(TestContainerListener.class::isInstance);
    }

    public static final class TestWrapper extends StandardWrapper {
        public TestWrapper() {
        }
    }

    public static final class TestLifecycleListener implements LifecycleListener {
        public TestLifecycleListener() {
        }

        @Override
        public void lifecycleEvent(LifecycleEvent event) {
        }
    }

    public static final class TestContainerListener implements ContainerListener {
        public TestContainerListener() {
        }

        @Override
        public void containerEvent(ContainerEvent event) {
        }
    }
}
