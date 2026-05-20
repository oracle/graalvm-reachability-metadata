/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import javax.management.Attribute;
import javax.management.DynamicMBean;

import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.modeler.AttributeInfo;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.OperationInfo;
import org.apache.tomcat.util.modeler.ParameterInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseModelMBeanTest {

    @Test
    void invokesManagedResourceMethods() throws Exception {
        Connector connector = new Connector("HTTP/1.1");
        ManagedBean managedBean = new ManagedBean();
        managedBean.setName("connector");
        managedBean.setType(Connector.class.getName());
        managedBean.addAttribute(attribute("scheme", String.class.getName()));
        managedBean.addOperation(operation("setProperty", String.class.getName(), String.class.getName()));
        DynamicMBean mbean = managedBean.createMBean(connector);

        mbean.setAttribute(new Attribute("scheme", "https"));
        Object result = mbean.invoke("setProperty", new Object[] { "relaxedPathChars", "[]" },
                new String[] { String.class.getName(), String.class.getName() });

        assertThat(mbean.getAttribute("scheme")).isEqualTo("https");
        assertThat(result).isEqualTo(Boolean.TRUE);
    }

    private AttributeInfo attribute(String name, String type) {
        AttributeInfo attribute = new AttributeInfo();
        attribute.setName(name);
        attribute.setType(type);
        return attribute;
    }

    private OperationInfo operation(String name, String... parameterTypes) {
        OperationInfo operation = new OperationInfo();
        operation.setName(name);
        operation.setReturnType(Boolean.TYPE.getName());
        for (String parameterType : parameterTypes) {
            ParameterInfo parameter = new ParameterInfo();
            parameter.setName(parameterType);
            parameter.setType(parameterType);
            operation.addParameter(parameter);
        }
        return operation;
    }
}
