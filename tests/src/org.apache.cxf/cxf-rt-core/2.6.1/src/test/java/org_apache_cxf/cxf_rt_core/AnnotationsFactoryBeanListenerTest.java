/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_core;

import static org.assertj.core.api.Assertions.assertThat;

import javax.xml.namespace.QName;
import javax.xml.transform.dom.DOMSource;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.annotations.DataBinding;
import org.apache.cxf.annotations.FactoryType;
import org.apache.cxf.bus.CXFBusFactory;
import org.apache.cxf.databinding.source.SourceDataBinding;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.EndpointException;
import org.apache.cxf.endpoint.EndpointImpl;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.factory.FactoryBeanListener.Event;
import org.apache.cxf.service.factory.ReflectionServiceFactoryBean;
import org.apache.cxf.service.invoker.Factory;
import org.apache.cxf.service.invoker.FactoryInvoker;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.Destination;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class AnnotationsFactoryBeanListenerTest {
    private Bus bus;

    @AfterEach
    void closeBus() {
        if (bus != null) {
            bus.shutdown(true);
        }
        BusFactory.setDefaultBus(null);
    }

    @Test
    void installsAnAnnotationConfiguredServiceFactory() throws EndpointException {
        bus = new CXFBusFactory().createBus();
        ReflectionServiceFactoryBean serviceFactory = new ReflectionServiceFactoryBean();
        serviceFactory.setBus(bus);
        serviceFactory.setServiceClass(ScopedDocumentService.class);
        Service service = serviceFactory.create();
        EndpointInfo endpointInfo = new EndpointInfo();
        endpointInfo.setName(new QName("urn:test", "scopedDocumentService"));
        Endpoint endpoint = new EndpointImpl(bus, service, endpointInfo);

        serviceFactory.sendEvent(
                Event.SERVER_CREATED,
                new EndpointServer(endpoint),
                new ScopedDocumentService("original"),
                ScopedDocumentService.class);

        FactoryInvoker invoker = (FactoryInvoker) service.getInvoker();
        Exchange exchange = new ExchangeImpl();
        exchange.put(Service.class, service);
        exchange.put(Endpoint.class, endpoint);
        ScopedDocumentService serviceObject =
                (ScopedDocumentService) invoker.getServiceObject(exchange);

        assertThat(serviceObject.getScope()).isEqualTo("ScopedDocumentService:request");
    }

    @DataBinding(SourceDataBinding.class)
    @FactoryType(factoryClass = ConfiguredFactory.class, args = "request")
    public static final class ScopedDocumentService {
        private final String scope;

        public ScopedDocumentService() {
            this("default");
        }

        ScopedDocumentService(String scope) {
            this.scope = scope;
        }

        public DOMSource transform(DOMSource source) {
            return source;
        }

        public String getScope() {
            return scope;
        }
    }

    public static final class ConfiguredFactory implements Factory {
        private final String scope;

        public ConfiguredFactory(Class<?> serviceClass, String[] arguments) {
            scope = serviceClass.getSimpleName() + ":" + arguments[0];
        }

        @Override
        public Object create(Exchange exchange) {
            return new ScopedDocumentService(scope);
        }

        @Override
        public void release(Exchange exchange, Object serviceObject) { }
    }

    private static final class EndpointServer implements Server {
        private final Endpoint endpoint;
        private boolean started;

        private EndpointServer(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void stop() {
            started = false;
        }

        @Override
        public void destroy() {
            started = false;
        }

        @Override
        public boolean isStarted() {
            return started;
        }

        @Override
        public Destination getDestination() {
            return null;
        }

        @Override
        public Endpoint getEndpoint() {
            return endpoint;
        }
    }
}
