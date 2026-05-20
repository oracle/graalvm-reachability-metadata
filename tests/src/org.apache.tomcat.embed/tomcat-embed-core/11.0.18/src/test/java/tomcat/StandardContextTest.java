/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import org.apache.catalina.ContainerListener;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.JreMemoryLeakPreventionListener;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardWrapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardContextTest {

    @Test
    void createWrapperInstantiatesConfiguredClasses() {
        StandardContext context = new StandardContext();
        context.setWrapperClass(StandardWrapper.class.getName());
        context.addWrapperLifecycle(JreMemoryLeakPreventionListener.class.getName());
        context.addWrapperListener(StandardContextContainerListener.class.getName());

        Wrapper wrapper = context.createWrapper();

        assertThat(wrapper).isInstanceOf(StandardWrapper.class);
        assertThat(wrapper.findLifecycleListeners()).hasAtLeastOneElementOfType(JreMemoryLeakPreventionListener.class);
        assertThat(wrapper.findContainerListeners()).hasAtLeastOneElementOfType(ContainerListener.class);
    }

}
