/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import javax.management.Attribute;
import javax.management.DynamicMBean;

import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseModelMBeanTest {
    @Test
    void readsWritesAndInvokesManagedResource() throws Exception {
        ManagedBean descriptor = ModelerSupport.descriptor();
        ContextEnvironment resource = new ContextEnvironment();
        resource.setName("catalog");
        resource.setProperty("theme", "dark");
        DynamicMBean mbean = descriptor.createMBean(resource);
        assertThat(mbean.getAttribute("name")).isEqualTo("catalog");
        mbean.setAttribute(new Attribute("name", "shop"));
        assertThat(resource.getName()).isEqualTo("shop");
        assertThat(mbean.getAttribute("name")).isEqualTo("shop");
        assertThat(mbean.invoke("getProperty", new Object[] {"theme"},
                new String[] {String.class.getName()})).isEqualTo("dark");
        assertThat(mbean.getAttribute("modelerType")).isEqualTo(ContextEnvironment.class.getName());
    }
}
