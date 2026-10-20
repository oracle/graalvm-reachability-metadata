/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_core;

import static org.assertj.core.api.Assertions.assertThat;

import javax.xml.transform.dom.DOMSource;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.annotations.DataBinding;
import org.apache.cxf.bus.CXFBusFactory;
import org.apache.cxf.databinding.source.SourceDataBinding;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.factory.ReflectionServiceFactoryBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class ReflectionServiceFactoryBeanTest {
    private Bus bus;

    @AfterEach
    void closeBus() {
        if (bus != null) {
            bus.shutdown(true);
        }
        BusFactory.setDefaultBus(null);
    }

    @Test
    void buildsAServiceModelFromAnAnnotatedJavaInterface() {
        bus = new CXFBusFactory().createBus();
        ReflectionServiceFactoryBean factory = new ReflectionServiceFactoryBean();
        factory.setBus(bus);
        factory.setServiceClass(DocumentService.class);

        Service service = factory.create();

        assertThat(service.getName().getLocalPart()).isEqualTo("DocumentService");
        assertThat(service.getServiceInfos().get(0).getInterface().getOperations())
                .extracting(operation -> operation.getName().getLocalPart())
                .contains("transform");
        assertThat(service.getDataBinding()).isInstanceOf(SourceDataBinding.class);
    }

    @DataBinding(SourceDataBinding.class)
    public interface DocumentService {
        DOMSource transform(DOMSource source) throws DocumentFault;
    }

    public static final class DocumentFault extends Exception {
        private final String detail;

        public DocumentFault(String message, String detail) {
            super(message);
            this.detail = detail;
        }

        public String getDetail() {
            return detail;
        }
    }
}
