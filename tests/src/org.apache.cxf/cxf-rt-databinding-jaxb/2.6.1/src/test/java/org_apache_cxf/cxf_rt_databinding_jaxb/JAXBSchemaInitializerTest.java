/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_databinding_jaxb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
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
import org.apache.cxf.service.model.FaultInfo;
import org.apache.cxf.service.model.InterfaceInfo;
import org.apache.cxf.service.model.MessagePartInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.service.model.ServiceInfo;
import org.apache.ws.commons.schema.XmlSchemaComplexType;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.XmlSchemaSequence;
import org.junit.jupiter.api.Test;

public class JAXBSchemaInitializerTest {
    private static final String NAMESPACE = "urn:cxf-schema-test";

    @Test
    void initializationBuildsSchemaForCustomException() {
        JaxbTestSupport.disableOptimizedAccessors();
        ServiceInfo service = new ServiceInfo();
        service.setName(new QName(NAMESPACE, "FaultService"));
        InterfaceInfo iface = service.createInterface(new QName(NAMESPACE, "FaultPort"));
        OperationInfo operation = iface.addOperation(new QName(NAMESPACE, "invoke"));
        FaultInfo fault = operation.addFault(
                new QName(NAMESPACE, "ServiceFaultMessage"), new QName(NAMESPACE, "ServiceFault"));
        fault.setProperty("elementName", new QName(NAMESPACE, "ServiceFault"));
        fault.setProperty(Class.class.getName(), ServiceFault.class);
        MessagePartInfo part = fault.addMessagePart(new QName(NAMESPACE, "ServiceFault"));
        part.setElementQName(new QName(NAMESPACE, "ServiceFault"));
        part.setTypeClass(ServiceFault.class);

        JAXBDataBinding binding = new JAXBDataBinding();
        binding.initialize(new ServiceImpl(service));

        assertThat(part.getXmlSchema()).isNotNull();
        assertThat(part.getProperty(JAXBDataBinding.class.getName() + ".CUSTOM_EXCEPTION"))
                .isEqualTo(Boolean.TRUE);
        assertThat(service.getSchema(NAMESPACE)).isNotNull();
    }

    @Test
    void initializationMapsRepeatedExceptionTypeForDistinctFaultElements() {
        JaxbTestSupport.disableOptimizedAccessors();
        ServiceInfo service = new ServiceInfo();
        service.setName(new QName(NAMESPACE, "RepeatedFaultService"));
        InterfaceInfo iface = service.createInterface(new QName(NAMESPACE, "RepeatedFaultPort"));
        MessagePartInfo firstPart = addFault(iface, "first", "FirstFault");
        MessagePartInfo secondPart = addFault(iface, "second", "SecondFault");

        JAXBDataBinding binding = new JAXBDataBinding();
        binding.initialize(new ServiceImpl(service));

        assertThat(firstPart.getXmlSchema()).isInstanceOf(XmlSchemaElement.class);
        assertThat(secondPart.getXmlSchema()).isInstanceOf(XmlSchemaElement.class);
        XmlSchemaElement[] faultElements = {
            (XmlSchemaElement) firstPart.getXmlSchema(),
            (XmlSchemaElement) secondPart.getXmlSchema()
        };
        List<String> elementNames = new ArrayList<String>();
        for (XmlSchemaElement faultElement : faultElements) {
            if (faultElement.getSchemaType() instanceof XmlSchemaComplexType) {
                XmlSchemaComplexType faultType =
                        (XmlSchemaComplexType) faultElement.getSchemaType();
                XmlSchemaSequence sequence = (XmlSchemaSequence) faultType.getParticle();
                for (Object item : sequence.getItems()) {
                    elementNames.add(((XmlSchemaElement) item).getName());
                }
            }
        }
        assertThat(elementNames).contains("Detail");
    }

    @Test
    void reflectionServiceFactoryBuildsFaultSchemaFromExceptionAccessors() {
        JaxbTestSupport.disableOptimizedAccessors();
        ExtensionManagerBus bus = new ExtensionManagerBus();
        try {
            JAXBDataBinding binding = new JAXBDataBinding();
            ReflectionServiceFactoryBean factory = new ReflectionServiceFactoryBean();
            factory.setBus(bus);
            factory.setServiceClass(FaultService.class);
            factory.setWrapped(false);
            factory.setDataBinding(binding);

            Service service = factory.create();
            FaultInfo fault = service.getServiceInfos().get(0).getInterface()
                    .getOperations().iterator().next().getFaults().iterator().next();

            assertThat(fault.getMessageParts().get(0).getXmlSchema()).isNotNull();
        } finally {
            bus.shutdown(true);
        }
    }

    private static MessagePartInfo addFault(
            InterfaceInfo iface, String operationName, String elementName) {
        OperationInfo operation = iface.addOperation(new QName(NAMESPACE, operationName));
        FaultInfo fault = operation.addFault(
                new QName(NAMESPACE, elementName + "Message"), new QName(NAMESPACE, elementName));
        fault.setProperty("elementName", new QName(NAMESPACE, elementName));
        fault.setProperty(Class.class.getName(), RepeatedFault.class);
        MessagePartInfo part = fault.addMessagePart(new QName(NAMESPACE, elementName));
        part.setElementQName(new QName(NAMESPACE, elementName));
        part.setTypeClass(RepeatedFault.class);
        return part;
    }

    public interface FaultService {
        String invoke(String value) throws ServiceFault;
    }

    @XmlType(name = "repeatedFaultType", namespace = NAMESPACE)
    @XmlAccessorType(XmlAccessType.PROPERTY)
    public static class RepeatedFault extends Exception {
        private String detail;
        private int[] codes;

        public RepeatedFault() {
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }

        public int[] getCodes() {
            return codes;
        }

        public void setCodes(int[] codes) {
            this.codes = codes.clone();
        }
    }

    @XmlAccessorType(XmlAccessType.PROPERTY)
    public static class ServiceFault extends Exception {
        private String detail;
        private int[] codes;

        public ServiceFault() {
        }

        public ServiceFault(String message) {
            super(message);
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }

        public int[] getCodes() {
            return codes;
        }

        public void setCodes(int[] codes) {
            this.codes = codes.clone();
        }
    }
}
