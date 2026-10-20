/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.xml.transform.dom.DOMSource;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.annotations.DataBinding;
import org.apache.cxf.bus.CXFBusFactory;
import org.apache.cxf.databinding.source.SourceDataBinding;
import org.apache.cxf.interceptor.FaultOutInterceptor.FaultInfoException;
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
    void createsConfiguredDataBindingsUsingSupportedConstructors() {
        bus = new CXFBusFactory().createBus();
        bus.setProperty(
                org.apache.cxf.databinding.DataBinding.class.getName(),
                FactoryAwareDataBinding.class);

        ReflectionServiceFactoryBean factory = new ReflectionServiceFactoryBean();
        factory.setBus(bus);
        factory.setServiceClass(ConstructorBoundService.class);
        Service constructorBoundService = factory.create();

        assertThat(constructorBoundService.getDataBinding())
                .isInstanceOf(FactoryAwareDataBinding.class);
        assertThat(((FactoryAwareDataBinding) constructorBoundService.getDataBinding()).factory)
                .isSameAs(factory);

        bus.setProperty(
                org.apache.cxf.databinding.DataBinding.class.getName(), DefaultDataBinding.class);
        ReflectionServiceFactoryBean defaultFactory = new ReflectionServiceFactoryBean();
        defaultFactory.setBus(bus);
        defaultFactory.setServiceClass(ConstructorBoundService.class);

        assertThat(defaultFactory.create().getDataBinding()).isInstanceOf(DefaultDataBinding.class);
    }

    @Test
    void modelsGenericArrayParametersAndFaultInfoExceptions() {
        bus = new CXFBusFactory().createBus();
        ReflectionServiceFactoryBean factory = new ReflectionServiceFactoryBean();
        factory.setBus(bus);
        factory.setServiceClass(AdvancedDocumentService.class);

        Service service = factory.create();

        assertThat(service.getServiceInfos().get(0).getInterface().getOperations())
                .extracting(operation -> operation.getName().getLocalPart())
                .contains("transformMany", "read");
        assertThat(service.getServiceInfos().get(0).getInterface().getOperations()).hasSize(2);
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

    public static final class FactoryAwareDataBinding extends SourceDataBinding {
        private final ReflectionServiceFactoryBean factory;

        public FactoryAwareDataBinding(ReflectionServiceFactoryBean factory) {
            this.factory = factory;
        }
    }

    public static final class DefaultDataBinding extends SourceDataBinding {
        public DefaultDataBinding() {}
    }

    public interface ConstructorBoundService {
        DOMSource transform(DOMSource source);
    }

    @DataBinding(SourceDataBinding.class)
    public interface AdvancedDocumentService {
        void transformMany(List<String>[] values);

        DOMSource read(DOMSource source) throws DetailedFault;
    }

    public static final class DetailedFault extends Exception implements FaultInfoException {
        private final FaultDetail faultInfo;

        public DetailedFault(String message, FaultDetail faultInfo) {
            super(message);
            this.faultInfo = faultInfo;
        }

        public FaultDetail getFaultInfo() {
            return faultInfo;
        }
    }

    public static final class FaultDetail {
        private String reason;

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
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
