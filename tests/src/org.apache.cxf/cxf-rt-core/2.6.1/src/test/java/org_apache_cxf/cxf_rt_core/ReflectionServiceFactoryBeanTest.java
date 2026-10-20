/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.transform.dom.DOMSource;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.annotations.DataBinding;
import org.apache.cxf.bus.CXFBusFactory;
import org.apache.cxf.databinding.source.SourceDataBinding;
import org.apache.cxf.interceptor.FaultOutInterceptor.FaultInfoException;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.factory.AbstractServiceConfiguration;
import org.apache.cxf.service.factory.ReflectionServiceFactoryBean;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.service.model.FaultInfo;
import org.apache.cxf.service.model.InterfaceInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.DestinationFactory;
import org.apache.cxf.transport.DestinationFactoryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ReflectionServiceFactoryBeanTest {
    private Bus bus;

    @TempDir Path temporaryDirectory;

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
    void modelsGenericArrayParametersAndExceptionFields() {
        bus = new CXFBusFactory().createBus();
        ReflectionServiceFactoryBean factory = new FaultModelFactory();
        factory.setBus(bus);
        factory.setServiceClass(AdvancedDocumentService.class);
        factory.getServiceConfigurations().add(0, new GenericHolderConfiguration());

        Service service = factory.create();
        InterfaceInfo serviceInterface = service.getServiceInfos().get(0).getInterface();
        OperationInfo transformMany =
                serviceInterface.getOperations().stream()
                        .filter(
                                operation ->
                                        operation
                                                .getName()
                                                .getLocalPart()
                                                .equals("transformMany"))
                        .findFirst()
                        .orElseThrow();
        OperationInfo read =
                serviceInterface.getOperations().stream()
                        .filter(operation -> operation.getName().getLocalPart().equals("read"))
                        .findFirst()
                        .orElseThrow();

        assertThat(
                        transformMany
                                .getUnwrappedOperation()
                                .getInput()
                                .getMessagePart(0)
                                .getTypeClass())
                .isEqualTo(List[][].class);
        assertThat(read.getFaults())
                .flatExtracting(fault -> fault.getMessageParts())
                .extracting(part -> part.getName().getLocalPart())
                .contains("faultInfo", "message");
    }

    @Test
    void associatesWsdlOperationsWithServiceMethods() throws IOException {
        Path wsdl = temporaryDirectory.resolve("document-service.wsdl");
        Files.writeString(
                wsdl,
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                             xmlns:tns="urn:test:document"
                             xmlns:transport="urn:test:transport"
                             xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                             targetNamespace="urn:test:document">
                  <message name="TransformInput">
                    <part name="source" type="xsd:anyType"/>
                  </message>
                  <message name="TransformOutput">
                    <part name="result" type="xsd:anyType"/>
                  </message>
                  <portType name="DocumentPortType">
                    <operation name="transform">
                      <input message="tns:TransformInput"/>
                      <output message="tns:TransformOutput"/>
                    </operation>
                  </portType>
                  <binding name="DocumentBinding" type="tns:DocumentPortType">
                    <operation name="transform">
                      <input/>
                      <output/>
                    </operation>
                  </binding>
                  <service name="DocumentService">
                    <port name="DocumentPort" binding="tns:DocumentBinding">
                      <transport:address/>
                    </port>
                  </service>
                </definitions>
                """);
        bus = new CXFBusFactory().createBus();
        bus.getExtension(DestinationFactoryManager.class)
                .registerDestinationFactory("urn:test:transport", new ModelDestinationFactory());
        ReflectionServiceFactoryBean factory = new ReflectionServiceFactoryBean();
        factory.setBus(bus);
        factory.setServiceClass(WsdlDocumentService.class);
        factory.setServiceName(new QName("urn:test:document", "DocumentService"));
        factory.setEndpointName(new QName("urn:test:document", "DocumentPort"));
        factory.setWsdlURL(wsdl.toUri().toString());

        Service service = factory.create();
        OperationInfo operation =
                service.getServiceInfos().get(0).getInterface().getOperations().iterator().next();

        assertThat(service.getName()).isEqualTo(new QName("urn:test:document", "DocumentService"));
        assertThat(operation.getName().getLocalPart()).isEqualTo("transform");
        assertThat(operation.getProperty(ReflectionServiceFactoryBean.METHOD)).isNotNull();
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
        void transformMany(GenericHolder<List<String>[]> values);

        DOMSource read(DOMSource source) throws DetailedFault;
    }

    public static final class GenericHolder<T> {
        private T value;

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            this.value = value;
        }
    }

    public static final class GenericHolderConfiguration extends AbstractServiceConfiguration {
        @Override
        public Boolean isHolder(Class<?> type, Type genericType) {
            return type == GenericHolder.class ? Boolean.TRUE : null;
        }

        @Override
        public Type getHolderType(Class<?> type, Type genericType) {
            if (type != GenericHolder.class) {
                return null;
            }
            return ((ParameterizedType) genericType).getActualTypeArguments()[0];
        }
    }

    public static final class FaultModelFactory extends ReflectionServiceFactoryBean {
        @Override
        protected FaultInfo addFault(
                InterfaceInfo service, OperationInfo operation, Class<?> exceptionClass) {
            FaultInfo fault = super.addFault(service, operation, exceptionClass);
            if (fault != null) {
                createFaultForException(exceptionClass, fault);
            }
            return fault;
        }
    }

    public static final class ModelDestinationFactory implements DestinationFactory {
        @Override
        public Destination getDestination(EndpointInfo endpoint) throws IOException {
            throw new IOException("This model-only transport does not create destinations");
        }

        @Override
        public Set<String> getUriPrefixes() {
            return Set.of("urn:test:transport");
        }

        @Override
        public List<String> getTransportIds() {
            return List.of("urn:test:transport");
        }
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

    @DataBinding(SourceDataBinding.class)
    public interface WsdlDocumentService {
        DOMSource transform(DOMSource source);
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
