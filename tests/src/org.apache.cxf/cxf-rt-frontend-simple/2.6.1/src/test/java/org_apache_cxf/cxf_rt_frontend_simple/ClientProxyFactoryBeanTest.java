/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_frontend_simple;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.xml.transform.Source;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.databinding.source.SourceDataBinding;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ClientProxyFactoryBeanTest {
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void createsClientProxyWithSourceDataBinding() {
        Bus bus = BusFactory.newInstance().createBus();
        Client client = null;

        try {
            ClientProxyFactoryBean clientFactory = new ClientProxyFactoryBean();
            clientFactory.setBus(bus);
            clientFactory.setServiceClass(TransformService.class);
            clientFactory.setDataBinding(new SourceDataBinding());
            clientFactory.setBindingId("http://cxf.apache.org/bindings/xformat");
            clientFactory.setAddress("local://client-proxy-" + UUID.randomUUID());

            TransformService proxy = clientFactory.create(TransformService.class);
            client = ClientProxy.getClient(proxy);

            assertThat(Proxy.isProxyClass(proxy.getClass())).isTrue();
            assertThat(client).isNotNull();
            assertThat(client.getEndpoint().getEndpointInfo().getAddress())
                    .isEqualTo(clientFactory.getAddress());
        } finally {
            if (client != null) {
                client.destroy();
            }
            bus.shutdown(true);
        }
    }

    public interface TransformService {
        Source transform(Source input);
    }
}
