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
import javax.xml.namespace.QName;
import org.apache.cxf.jaxb.JAXBDataBinding;
import org.apache.cxf.service.ServiceImpl;
import org.apache.cxf.service.model.FaultInfo;
import org.apache.cxf.service.model.InterfaceInfo;
import org.apache.cxf.service.model.MessagePartInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.service.model.ServiceInfo;
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
