/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.management.Attribute;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;

import org.jgroups.annotations.Component;
import org.jgroups.annotations.ManagedAttribute;
import org.jgroups.annotations.ManagedOperation;
import org.jgroups.jmx.ResourceDMBean;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceDMBeanTest {
    @Test
    void exposesManagedAttributesAndOperations() throws Exception {
        ManagedResource resource = new ManagedResource();
        ResourceDMBean bean = new ResourceDMBean(resource);

        assertThat(bean.getAttribute("counter")).isEqualTo(7);
        bean.setAttribute(new Attribute("counter", 11));
        assertThat(bean.getAttribute("counter")).isEqualTo(11);

        assertThat(bean.getAttribute("label")).isEqualTo("initial");
        bean.setAttribute(new Attribute("label", "updated"));
        assertThat(bean.getAttribute("label")).isEqualTo("updated");

        MBeanInfo info = bean.getMBeanInfo();
        assertThat(info.getAttributes()).extracting(MBeanAttributeInfo::getName).contains("counter", "label");
        assertThat(info.getOperations()).extracting(MBeanOperationInfo::getName).contains("describe");
    }

    @Test
    void invokesOperationsOnComponentsAndWrappedObject() throws Exception {
        CompositeResource resource = new CompositeResource();
        ResourceDMBean bean = new ResourceDMBean(resource);

        Object componentResult = bean.invoke("componentOperation", new Object[] {"item", 3},
                new String[] {String.class.getName(), int.class.getName()});
        assertThat(componentResult).isEqualTo("item:3");

        Object hostResult = bean.invoke("hostOperation", new Object[] {"host", 4},
                new String[] {String.class.getName(), int.class.getName()});
        assertThat(hostResult).isEqualTo("host:4");
    }

    @Test
    void dumpsManagedFieldAndGetterStatistics() {
        ManagedResource resource = new ManagedResource();
        resource.setLabel("dumped");
        Map<String, Object> values = new LinkedHashMap<>();

        ResourceDMBean.dumpStats(resource, "resource", values);

        assertThat(values).containsEntry("resource.counter", "7")
                .containsEntry("resource.label", "dumped");
    }

    public static class ManagedResource {
        @ManagedAttribute(writable = true)
        private int counter = 7;

        private String label = "initial";

        @ManagedAttribute(name = "label", writable = true)
        public String getLabel() {
            return label;
        }

        @ManagedAttribute(name = "label", writable = true)
        public void setLabel(String newLabel) {
            label = newLabel;
        }

        @ManagedOperation(description = "returns a stable resource description")
        public String describe() {
            return label + ':' + counter;
        }
    }

    public static class CompositeResource {
        @Component
        private final OperationComponent component = new OperationComponent();

        @ManagedOperation(description = "operation on the wrapped object")
        public String hostOperation(String value, int count) {
            return value + ':' + count;
        }
    }

    public static class OperationComponent {
        @ManagedOperation(description = "operation on a component")
        public String componentOperation(String value, int count) {
            return value + ':' + count;
        }
    }
}
