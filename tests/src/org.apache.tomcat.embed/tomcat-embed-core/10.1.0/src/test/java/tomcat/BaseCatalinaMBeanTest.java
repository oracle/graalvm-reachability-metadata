/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import org.apache.catalina.core.StandardHost;
import org.apache.catalina.mbeans.ContainerMBean;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseCatalinaMBeanTest {
    @Test
    void addsConfiguredLifecycleListenerThroughManagementApi() throws Exception {
        StandardHost host = new StandardHost();
        ContainerMBean mbean = new ContainerMBean();
        mbean.setManagedResource(host, "ObjectReference");
        mbean.addLifecycleListener(StandardContextTest.WrapperLifecycle.class.getName());
        assertThat(host.findLifecycleListeners()).hasSize(1);
        assertThat(mbean.findLifecycleListenerNames()).contains(StandardContextTest.WrapperLifecycle.class.getName());
    }
}
