/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.Locale;

import javax.management.Attribute;
import javax.management.DynamicMBean;

import org.apache.tomcat.util.modeler.AttributeInfo;
import org.apache.tomcat.util.modeler.BaseModelMBean;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.OperationInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BaseModelMBeanTest {
    @Test
    void invokesAttributesAndOperationsImplementedByTheMBean() throws Exception {
        ManagedBean descriptor = new ManagedBean();
        descriptor.setName("Application");
        descriptor.setClassName(ApplicationMBean.class.getName());
        AttributeInfo attribute = new AttributeInfo();
        attribute.setName("label");
        attribute.setType(String.class.getName());
        descriptor.addAttribute(attribute);
        OperationInfo operation = new OperationInfo();
        operation.setName("uppercase");
        operation.setReturnType(String.class.getName());
        descriptor.addOperation(operation);
        DynamicMBean bean = descriptor.createMBean(null);
        bean.setAttribute(new Attribute("label", "orders"));
        assertEquals("orders", bean.getAttribute("label"));
        assertEquals("ORDERS", bean.invoke("uppercase", new Object[0], new String[0]));
    }

    public static class ApplicationMBean extends BaseModelMBean {
        private String label;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String uppercase() {
            return label.toUpperCase(Locale.ROOT);
        }
    }
}
