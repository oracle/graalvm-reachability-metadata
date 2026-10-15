/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import javax.management.Attribute;
import javax.management.DynamicMBean;

import org.apache.tomcat.util.modeler.AttributeInfo;
import org.apache.tomcat.util.modeler.BaseModelMBean;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.OperationInfo;
import org.apache.tomcat.util.modeler.ParameterInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseModelMBeanTest {

    @Test
    void invokesModelMBeanMethodsThroughDynamicMBeanApi() throws Exception {
        DynamicMBean mBean = createMBean();

        assertThat(mBean.getAttribute("localText")).isEqualTo("local");
        mBean.setAttribute(new Attribute("localText", "changed"));
        assertThat(mBean.invoke("describeLocal", new Object[] {"prefix"},
                new String[] {String.class.getName()})).isEqualTo("prefix:changed");
    }

    @Test
    void resolvesAttributeTypesWithoutThreadContextClassLoader() throws Exception {
        DynamicMBean mBean = createMBean();
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();

        try {
            currentThread.setContextClassLoader(null);
            mBean.setAttribute(new Attribute("localText", "resolvedByClassForName"));
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }

        assertThat(mBean.getAttribute("localText")).isEqualTo("resolvedByClassForName");
    }

    private static DynamicMBean createMBean() throws Exception {
        ManagedBean managedBean = new ManagedBean();
        managedBean.setClassName(LocalModelMBean.class.getName());
        managedBean.setName("testBean");
        managedBean.setDescription("Test managed bean");
        managedBean.addAttribute(localAttribute());
        managedBean.addOperation(localOperation());
        return managedBean.createMBean(null);
    }

    private static AttributeInfo localAttribute() {
        AttributeInfo attribute = new AttributeInfo();
        attribute.setName("localText");
        attribute.setDescription("A property declared by the model mbean class");
        attribute.setType(String.class.getName());
        return attribute;
    }

    private static OperationInfo localOperation() {
        OperationInfo operation = new OperationInfo();
        operation.setName("describeLocal");
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

    public static class LocalModelMBean extends BaseModelMBean {
        private String localText = "local";

        public String getLocalText() {
            return localText;
        }

        public void setLocalText(String localText) {
            this.localText = localText;
        }

        public String describeLocal(String prefix) {
            return prefix + ':' + localText;
        }
    }
}
