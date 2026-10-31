package tomcat;

import org.apache.tomcat.util.modeler.AttributeInfo;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.OperationInfo;
import org.apache.tomcat.util.modeler.ParameterInfo;

final class ModelerSupport {
    private ModelerSupport() {
    }

    static ManagedBean descriptor() {
        ManagedBean descriptor = new ManagedBean();
        descriptor.setName("catalog");
        descriptor.addAttribute(attribute("name"));
        descriptor.addOperation(operation("getProperty", Object.class));
        return descriptor;
    }

    static AttributeInfo attribute(String name) {
        AttributeInfo attribute = new AttributeInfo();
        attribute.setName(name);
        attribute.setType(String.class.getName());
        return attribute;
    }

    static OperationInfo operation(String name, Class<?> returnType) {
        OperationInfo operation = new OperationInfo();
        operation.setName(name);
        operation.setReturnType(returnType.getName());
        ParameterInfo parameter = new ParameterInfo();
        parameter.setName("value");
        parameter.setType(String.class.getName());
        operation.addParameter(parameter);
        return operation;
    }
}
