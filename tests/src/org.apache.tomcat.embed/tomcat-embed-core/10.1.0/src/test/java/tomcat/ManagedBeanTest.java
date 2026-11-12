/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import javax.management.Attribute;
import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;

import org.apache.tomcat.util.modeler.ManagedBean;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ManagedBeanTest {
    @Test
    void createsCustomManagementBeanFromDescriptor() throws Exception {
        ManagedBean descriptor = ModelerSupport.descriptor();
        descriptor.setClassName(ModelerSupport.CustomMBean.class.getName());
        DynamicMBean mbean = descriptor.createMBean(null);
        assertThat(mbean.getMBeanInfo().getAttributes()).extracting(MBeanAttributeInfo::getName).contains("label");
        mbean.setAttribute(new Attribute("label", "managed"));
        assertThat(mbean.getAttribute("label")).isEqualTo("managed");
        assertThat(mbean.invoke("append", new Object[] {"!"}, new String[] {String.class.getName()}))
                .isEqualTo("managed!");
    }
}
