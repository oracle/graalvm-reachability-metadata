/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_databinding_jaxb;

import static org.assertj.core.api.Assertions.assertThat;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;
import org.apache.cxf.jaxb.JAXBDataBinding;
import org.apache.cxf.service.ServiceImpl;
import org.apache.cxf.service.model.InterfaceInfo;
import org.apache.cxf.service.model.MessageInfo;
import org.apache.cxf.service.model.MessagePartInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.service.model.ServiceInfo;
import org.junit.jupiter.api.Test;

public class JAXBContextInitializerTest {
    private static final String NAMESPACE = "urn:cxf-context-test";

    @Test
    void initializationDiscoversPropertyBasedModel() {
        ServiceInfo serviceInfo = serviceWithPart(PropertyModel.class);
        JAXBDataBinding binding = new JAXBDataBinding();

        binding.initialize(new ServiceImpl(serviceInfo));

        assertThat(binding.getContext()).isNotNull();
        assertThat(binding.getContextClasses()).contains(PropertyModel.class);
        assertThat(serviceInfo.getSchemas()).isNotEmpty();
    }

    private static ServiceInfo serviceWithPart(Class<?> type) {
        ServiceInfo service = new ServiceInfo();
        service.setName(new QName(NAMESPACE, "ContextService"));
        InterfaceInfo iface = service.createInterface(new QName(NAMESPACE, "ContextPort"));
        OperationInfo operation = iface.addOperation(new QName(NAMESPACE, "echo"));
        MessageInfo input = operation.createMessage(
                new QName(NAMESPACE, "echoRequest"), MessageInfo.Type.INPUT);
        operation.setInput("echoRequest", input);
        MessagePartInfo part = input.addMessagePart(new QName(NAMESPACE, "model"));
        part.setTypeClass(type);
        return service;
    }

    @XmlType(name = "propertyModel", namespace = NAMESPACE)
    @XmlAccessorType(XmlAccessType.PROPERTY)
    public static class PropertyModel {
        private String value;

        public PropertyModel() {
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
