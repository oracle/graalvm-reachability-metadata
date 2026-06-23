/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import javax.management.Attribute;
import javax.management.DynamicMBean;

import org.apache.tomcat.util.modeler.AttributeInfo;
import org.apache.tomcat.util.modeler.BaseModelMBean;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.OperationInfo;
import org.apache.tomcat.util.modeler.ParameterInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseModelMBeanTest extends BaseModelMBean {

    private String localText = "local";

    @Test
    void invokesManagedBeanAndResourceMethodsThroughDynamicMBeanApi() throws Exception {
        DynamicMBean localMBean = createMBean(null, localAttribute(), localOperation());

        assertThat(localMBean.getAttribute("localText")).isEqualTo("local");
        localMBean.setAttribute(new Attribute("localText", "changed"));
        assertThat(localMBean.invoke("describeLocal", new Object[] {"prefix"},
                new String[] {String.class.getName()})).isEqualTo("prefix:changed");

        ManagedResource resource = new ManagedResource();
        DynamicMBean resourceMBean = createMBean(resource, resourceAttribute(), resourceOperation());

        assertThat(resourceMBean.getAttribute("resourceText")).isEqualTo("resource");
        resourceMBean.setAttribute(new Attribute("resourceText", "updated"));
        assertThat(resourceMBean.invoke("describeResource", new Object[] {"value"},
                new String[] {String.class.getName()})).isEqualTo("value:updated");
    }

    @Test
    void resolvesAttributeTypesWithoutThreadContextClassLoader() throws Exception {
        ManagedResource resource = new ManagedResource();
        DynamicMBean resourceMBean = createMBean(resource, resourceAttribute(), resourceOperation());
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();

        try {
            currentThread.setContextClassLoader(null);
            resourceMBean.setAttribute(new Attribute("resourceText", "resolvedByClassForName"));
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }

        assertThat(resourceMBean.getAttribute("resourceText")).isEqualTo("resolvedByClassForName");
    }

    public String getLocalText() {
        return localText;
    }

    public void setLocalText(String localText) {
        this.localText = localText;
    }

    public String describeLocal(String prefix) {
        return prefix + ':' + localText;
    }

    private static DynamicMBean createMBean(Object resource, AttributeInfo attribute, OperationInfo operation)
            throws Exception {
        ManagedBean managedBean = new ManagedBean();
        managedBean.setClassName(BaseModelMBeanTest.class.getName());
        managedBean.setName("testBean");
        managedBean.setDescription("Test managed bean");
        managedBean.addAttribute(attribute);
        managedBean.addOperation(operation);
        return managedBean.createMBean(resource);
    }

    private static AttributeInfo localAttribute() {
        AttributeInfo attribute = new AttributeInfo();
        attribute.setName("localText");
        attribute.setDescription("A property declared by the model mbean class");
        attribute.setType(String.class.getName());
        return attribute;
    }

    private static OperationInfo localOperation() {
        return operation("describeLocal");
    }

    private static AttributeInfo resourceAttribute() {
        AttributeInfo attribute = new AttributeInfo();
        attribute.setName("resourceText");
        attribute.setDescription("A property declared by the managed resource");
        attribute.setType(String.class.getName());
        return attribute;
    }

    private static OperationInfo resourceOperation() {
        return operation("describeResource");
    }

    private static OperationInfo operation(String name) {
        OperationInfo operation = new OperationInfo();
        operation.setName(name);
        operation.setDescription("Describe the current property value");
        operation.setReturnType(String.class.getName());
        operation.addParameter(parameter("prefix", String.class.getName()));
        return operation;
    }

    private static ParameterInfo parameter(String name, String type) {
        ParameterInfo parameter = new ParameterInfo();
        parameter.setName(name);
        parameter.setDescription("Operation parameter");
        parameter.setType(type);
        return parameter;
    }

    public static class ManagedResource {
        private String resourceText = "resource";

        public String getResourceText() {
            return resourceText;
        }

        public void setResourceText(String resourceText) {
            this.resourceText = resourceText;
        }

        public String describeResource(String prefix) {
            return prefix + ':' + resourceText;
        }
    }
}
