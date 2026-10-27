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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.management.Attribute;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceDMBeanTest {
    static {
        configureJGroupsLoopbackDefaults();
    }

    @BeforeAll
    static void configureLoopbackDefaults() {
        configureJGroupsLoopbackDefaults();
    }

    @Test
    void exposesAnnotatedFieldsMethodsAndComponentOperations() throws Exception {
        ManagedResource resource = new ManagedResource();
        ResourceDMBean bean = new ResourceDMBean(resource);

        MBeanInfo info = bean.getMBeanInfo();
        assertThat(Arrays.stream(info.getAttributes()).map(MBeanAttributeInfo::getName))
                .contains("counter", "message", "component_count");

        assertThat(bean.getAttribute("counter")).isEqualTo(7);
        bean.setAttribute(new Attribute("counter", 11));
        assertThat(bean.getAttribute("counter")).isEqualTo(11);
        assertThat(bean.getAttribute("message")).isEqualTo("main:11");

        Object componentReply = bean.invoke(
                "componentGreeting",
                new Object[] {"node-a"},
                new String[] {String.class.getName()});
        Object ownerReply = bean.invoke(
                "ownerGreeting",
                new Object[] {"node-b"},
                new String[] {String.class.getName()});

        assertThat(componentReply).isEqualTo("component:node-a");
        assertThat(ownerReply).isEqualTo("owner:node-b:11");
    }

    @Test
    void dumpsAnnotatedFieldAndGetterStatistics() {
        ManagedResource resource = new ManagedResource();
        resource.counter = 13;
        Map<String, Object> values = new LinkedHashMap<>();

        ResourceDMBean.dumpStats(resource, "sample", values);

        assertThat(values)
                .containsEntry("sample.counter", "13")
                .containsEntry("sample.message", "main:13");
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }

    public static class ManagedResource {
        @ManagedAttribute(description = "Mutable counter", writable = true)
        private int counter = 7;

        @Component(name = "component")
        private final ManagedComponent component = new ManagedComponent();

        @ManagedAttribute(description = "Message assembled from the current counter")
        public String getMessage() {
            return "main:" + counter;
        }

        @ManagedOperation(description = "Greets from the owning resource")
        public String ownerGreeting(String name) {
            return "owner:" + name + ':' + counter;
        }
    }

    public static class ManagedComponent {
        @ManagedAttribute(name = "component_count", description = "Component counter", writable = true)
        private long count = 3;

        @ManagedOperation(description = "Greets from a component")
        public String componentGreeting(String name) {
            return "component:" + name;
        }
    }
}
