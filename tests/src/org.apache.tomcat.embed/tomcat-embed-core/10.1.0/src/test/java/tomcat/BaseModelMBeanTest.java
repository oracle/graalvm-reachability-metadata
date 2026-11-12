/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import javax.management.Attribute;
import javax.management.DynamicMBean;

import org.apache.tomcat.util.modeler.ManagedBean;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseModelMBeanTest {
    @Test
    void readsWritesAndInvokesManagedResource() throws Exception {
        ModelerSupport.Resource resource = new ModelerSupport.Resource();
        DynamicMBean mbean = ModelerSupport.descriptor().createMBean(resource);
        mbean.setAttribute(new Attribute("label", "catalog"));
        assertThat(mbean.getAttribute("label")).isEqualTo("catalog");
        assertThat(mbean.invoke("append", new Object[] {"-updated"}, new String[] {String.class.getName()}))
                .isEqualTo("catalog-updated");
        assertThat(resource.getLabel()).isEqualTo("catalog-updated");
    }

    @Test
    void dispatchesAttributesAndOperationsOnTheMBeanItself() throws Exception {
        ManagedBean descriptor = ModelerSupport.descriptor();
        descriptor.setClassName(ModelerSupport.CustomMBean.class.getName());
        DynamicMBean mbean = descriptor.createMBean(null);
        mbean.setAttribute(new Attribute("label", "local"));
        assertThat(mbean.getAttribute("label")).isEqualTo("local");
        assertThat(mbean.invoke("append", new Object[] {"-bean"}, new String[] {String.class.getName()}))
                .isEqualTo("local-bean");
    }
}
