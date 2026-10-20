/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_databinding_jaxb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;
import org.apache.cxf.bus.extension.ExtensionManagerBus;
import org.apache.cxf.jaxb.JAXBDataBinding;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.ServiceImpl;
import org.apache.cxf.service.factory.ReflectionServiceFactoryBean;
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
        JaxbTestSupport.disableOptimizedAccessors();
        ServiceInfo serviceInfo = serviceWithPart(PropertyModel.class);
        JAXBDataBinding binding = new JAXBDataBinding();

        binding.initialize(new ServiceImpl(serviceInfo));

        assertThat(binding.getContext()).isNotNull();
        assertThat(binding.getContextClasses()).contains(PropertyModel.class);
        assertThat(serviceInfo.getSchemas()).isNotEmpty();
    }

    @Test
    void serviceFactoryDiscoversCollectionElementType() {
        JaxbTestSupport.disableOptimizedAccessors();
        ExtensionManagerBus bus = new ExtensionManagerBus();
        try {
            JAXBDataBinding binding = new JAXBDataBinding();
            ReflectionServiceFactoryBean factory = new ReflectionServiceFactoryBean();
            factory.setBus(bus);
            factory.setServiceClass(CollectionService.class);
            factory.setWrapped(false);
            factory.setDataBinding(binding);

            Service service = factory.create();
            MessagePartInfo part = service.getServiceInfos().get(0).getInterface()
                    .getOperations().iterator().next().getInput().getMessagePart(0);

            assertThat(part.getTypeClass()).isEqualTo(CollectionItem[].class);
            assertThat(binding.getContextClasses()).contains(CollectionItem.class);
        } finally {
            bus.shutdown(true);
        }
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

    public interface CollectionService {
        void accept(List<CollectionItem> items);
    }

    @XmlType(name = "collectionItem", namespace = NAMESPACE)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class CollectionItem {
        public String value;

        public CollectionItem() {
        }
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
