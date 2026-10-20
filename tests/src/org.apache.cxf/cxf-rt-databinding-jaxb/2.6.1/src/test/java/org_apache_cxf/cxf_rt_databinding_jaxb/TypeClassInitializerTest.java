/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_databinding_jaxb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Iterator;
import javax.xml.namespace.QName;
import org.apache.cxf.common.jaxb.JAXBUtils.JCodeModel;
import org.apache.cxf.common.jaxb.JAXBUtils.JType;
import org.apache.cxf.common.jaxb.JAXBUtils.Mapping;
import org.apache.cxf.common.jaxb.JAXBUtils.S2JJAXBModel;
import org.apache.cxf.common.jaxb.JAXBUtils.TypeAndAnnotation;
import org.apache.cxf.endpoint.dynamic.TypeClassInitializer;
import org.apache.cxf.service.model.InterfaceInfo;
import org.apache.cxf.service.model.MessageInfo;
import org.apache.cxf.service.model.MessagePartInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.service.model.ServiceInfo;
import org.junit.jupiter.api.Test;

public class TypeClassInitializerTest {
    private static final String NAMESPACE = "urn:cxf-type-class-test";

    @Test
    void mapsNestedJaxbArrayTypeToJavaArrayClass() {
        QName typeName = new QName(NAMESPACE, "matrix");
        ServiceInfo service = new ServiceInfo();
        service.setName(new QName(NAMESPACE, "ArrayService"));
        InterfaceInfo iface = service.createInterface(new QName(NAMESPACE, "ArrayPort"));
        OperationInfo operation = iface.addOperation(new QName(NAMESPACE, "read"));
        MessageInfo message = operation.createMessage(
                new QName(NAMESPACE, "readResponse"), MessageInfo.Type.OUTPUT);
        operation.setOutput("readResponse", message);
        MessagePartInfo part = message.addMessagePart(new QName(NAMESPACE, "matrix"));
        part.setTypeQName(typeName);

        JType stringType = new NamedType(String.class.getName());
        S2JJAXBModel model = new ArrayModel(typeName, new ArrayType(new ArrayType(stringType)));
        TypeClassInitializer initializer = new TypeClassInitializer(service, model, true);

        initializer.begin(part);

        assertThat(part.getTypeClass()).isEqualTo(String[][].class);
    }

    private static final class ArrayModel implements S2JJAXBModel {
        private final QName typeName;
        private final TypeAndAnnotation type;

        private ArrayModel(QName typeName, JType type) {
            this.typeName = typeName;
            this.type = new ModelType(type);
        }

        public JCodeModel generateCode(Object codeWriter, Object errorListener) {
            return null;
        }

        public Mapping get(QName name) {
            return null;
        }

        public TypeAndAnnotation getJavaType(QName name) {
            return typeName.equals(name) ? type : null;
        }
    }

    private static final class ModelType implements TypeAndAnnotation {
        private final JType type;

        private ModelType(JType type) {
            this.type = type;
        }

        public JType getTypeClass() {
            return type;
        }
    }

    private static final class ArrayType implements JType {
        private final JType elementType;

        private ArrayType(JType elementType) {
            this.elementType = elementType;
        }

        public boolean isArray() {
            return true;
        }

        public JType elementType() {
            return elementType;
        }

        public boolean isPrimitive() {
            return false;
        }

        public String binaryName() {
            return name();
        }

        public String fullName() {
            return name();
        }

        public String name() {
            return elementType.name() + "[]";
        }

        public Iterator<JType> classes() {
            return Collections.<JType>emptyList().iterator();
        }
    }

    private static final class NamedType implements JType {
        private final String name;

        private NamedType(String name) {
            this.name = name;
        }

        public boolean isArray() {
            return false;
        }

        public JType elementType() {
            return null;
        }

        public boolean isPrimitive() {
            return false;
        }

        public String binaryName() {
            return name;
        }

        public String fullName() {
            return name;
        }

        public String name() {
            return name;
        }

        public Iterator<JType> classes() {
            return Collections.<JType>emptyList().iterator();
        }
    }
}
