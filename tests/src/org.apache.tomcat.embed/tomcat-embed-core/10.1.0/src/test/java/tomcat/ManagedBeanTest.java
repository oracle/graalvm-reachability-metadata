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
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.OperationInfo;
import org.apache.tomcat.util.modeler.ParameterInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ManagedBeanTest {
    @Test
    void exposesResourceBeanThroughDynamicMBean() throws Exception {
        ManagedBean descriptor = new ManagedBean();
        descriptor.setName("Counter");
        AttributeInfo attribute = new AttributeInfo();
        attribute.setName("count");
        attribute.setType("int");
        descriptor.addAttribute(attribute);
        OperationInfo operation = new OperationInfo();
        operation.setName("increment");
        operation.setReturnType("int");
        ParameterInfo parameter = new ParameterInfo();
        parameter.setName("amount");
        parameter.setType("int");
        operation.addParameter(parameter);
        descriptor.addOperation(operation);
        Counter resource = new Counter();
        DynamicMBean bean = descriptor.createMBean(resource);
        bean.setAttribute(new Attribute("count", 7));
        assertEquals(7, bean.getAttribute("count"));
        assertEquals(12, bean.invoke("increment", new Object[] {5}, new String[] {"int"}));
        assertEquals(12, resource.getCount());
    }

    public static class Counter {
        private int count;

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public int increment(int amount) {
            count += amount;
            return count;
        }
    }
}
