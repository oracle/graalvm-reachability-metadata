/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import org.apache.tomcat.util.modeler.AttributeInfo;
import org.apache.tomcat.util.modeler.BaseModelMBean;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.OperationInfo;
import org.apache.tomcat.util.modeler.ParameterInfo;

public final class ModelerSupport {
    private ModelerSupport() {
    }

    public static ManagedBean descriptor() {
        ManagedBean bean = new ManagedBean();
        bean.setName("Counter");
        AttributeInfo attribute = new AttributeInfo();
        attribute.setName("label");
        attribute.setType(String.class.getName());
        bean.addAttribute(attribute);
        OperationInfo operation = new OperationInfo();
        operation.setName("append");
        operation.setReturnType(String.class.getName());
        ParameterInfo parameter = new ParameterInfo();
        parameter.setName("suffix");
        parameter.setType(String.class.getName());
        operation.addParameter(parameter);
        bean.addOperation(operation);
        return bean;
    }

    public static AttributeInfo attribute(String name) {
        AttributeInfo attribute = new AttributeInfo();
        attribute.setName(name);
        attribute.setType(String.class.getName());
        return attribute;
    }

    public static OperationInfo operation(String name, Class<?> returnType) {
        OperationInfo operation = new OperationInfo();
        operation.setName(name);
        operation.setReturnType(returnType.getName());
        ParameterInfo parameter = new ParameterInfo();
        parameter.setName("value");
        parameter.setType(String.class.getName());
        operation.addParameter(parameter);
        return operation;
    }

    public static class Resource {
        private String label = "initial";

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String append(String suffix) {
            label += suffix;
            return label;
        }
    }

    public static class CustomMBean extends BaseModelMBean {
        private final Resource delegate = new Resource();

        public CustomMBean() throws Exception {
            super();
        }

        public String getLabel() {
            return delegate.getLabel();
        }

        public void setLabel(String label) {
            delegate.setLabel(label);
        }

        public String append(String suffix) {
            return delegate.append(suffix);
        }
    }
}
