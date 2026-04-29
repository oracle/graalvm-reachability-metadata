/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.annotations.Component;
import org.jgroups.annotations.ManagedAttribute;
import org.jgroups.annotations.ManagedOperation;
import org.jgroups.jmx.ResourceDMBean;
import org.junit.jupiter.api.Test;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceDMBeanTest {
    @Test
    void exposesAnnotatedFieldsMethodsAndComponentOperations() throws Exception {
        ManagedService service = new ManagedService();
        ResourceDMBean bean = new ResourceDMBean(service);

        MBeanInfo info = bean.getMBeanInfo();
        assertThat(attributeNames(info)).contains("field_value", "method_value", "component_counter");
        assertThat(bean.getAttribute("field_value")).isEqualTo("initial");
        assertThat(bean.getAttribute("method_value")).isEqualTo("method-initial");
        assertThat(bean.getAttribute("component_counter")).isEqualTo(3);

        bean.setAttribute(new Attribute("field_value", "changed"));
        AttributeList updatedAttributes = new AttributeList();
        updatedAttributes.add(new Attribute("component_counter", 8));
        assertThat(bean.setAttributes(updatedAttributes).asList()).hasSize(1);

        assertThat(bean.getAttribute("field_value")).isEqualTo("changed");
        assertThat(bean.getAttribute("component_counter")).isEqualTo(8);
        assertThat(bean.invoke("describe", new Object[] {"root", 5},
            new String[] {String.class.getName(), int.class.getName()})).isEqualTo("root:5:changed");
        assertThat(bean.invoke("componentOperation", new Object[] {"component", 4},
            new String[] {String.class.getName(), int.class.getName()})).isEqualTo("component:12");
    }

    @Test
    void dumpsFieldAndMethodStatistics() {
        ManagedService service = new ManagedService();
        Map<String, Object> stats = new LinkedHashMap<>();

        ResourceDMBean.dumpStats(service, "service", stats);

        assertThat(stats).containsEntry("service.field_value", "initial")
            .containsEntry("service.method_value", "method-initial");
    }

    private static String[] attributeNames(MBeanInfo info) {
        MBeanAttributeInfo[] attributes = info.getAttributes();
        String[] names = new String[attributes.length];
        for(int i = 0; i < attributes.length; i++) {
            names[i] = attributes[i].getName();
        }
        return names;
    }

    public static class ManagedService {
        @ManagedAttribute(name = "field_value", writable = true)
        private String fieldValue = "initial";

        @Component
        private final ManagedComponent component = new ManagedComponent();

        @ManagedAttribute(name = "method_value")
        public String getMethodValue() {
            return "method-" + fieldValue;
        }

        @ManagedOperation
        public String describe(String prefix, int count) {
            return prefix + ":" + count + ":" + fieldValue;
        }
    }

    public static class ManagedComponent {
        @ManagedAttribute(name = "component_counter", writable = true)
        private int counter = 3;

        @ManagedOperation
        public String componentOperation(String prefix, int increment) {
            return prefix + ":" + (counter + increment);
        }
    }
}
