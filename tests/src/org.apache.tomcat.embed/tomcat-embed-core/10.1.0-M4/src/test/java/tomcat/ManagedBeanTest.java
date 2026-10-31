/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import javax.management.Attribute;
import javax.management.DynamicMBean;

import org.apache.tomcat.util.modeler.BaseModelMBean;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ManagedBeanTest {
    @Test
    void constructsConfiguredMBeanAndDispatchesToItsOwnMethods() throws Exception {
        ManagedBean descriptor = new ManagedBean();
        descriptor.setName("labelled");
        descriptor.setClassName(LabelledMBean.class.getName());
        descriptor.addAttribute(ModelerSupport.attribute("label"));
        descriptor.addOperation(ModelerSupport.operation("greet", String.class));
        DynamicMBean mbean = descriptor.createMBean(null);
        mbean.setAttribute(new Attribute("label", "catalog"));
        assertThat(mbean.getAttribute("label")).isEqualTo("catalog");
        assertThat(mbean.invoke("greet", new Object[] {"reader"},
                new String[] {String.class.getName()})).isEqualTo("catalog:reader");
        assertThat(mbean.getMBeanInfo().getClassName()).isEqualTo(LabelledMBean.class.getName());
    }

    public static class LabelledMBean extends BaseModelMBean {
        private String label;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String greet(String name) {
            return label + ":" + name;
        }
    }
}
