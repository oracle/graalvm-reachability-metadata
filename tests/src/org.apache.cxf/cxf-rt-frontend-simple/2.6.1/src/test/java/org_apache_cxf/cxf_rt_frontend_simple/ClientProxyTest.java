/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_frontend_simple;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Closeable;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ClientProxyTest {
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void closesGeneratedClientThroughCloseableContract() throws Exception {
        Bus bus = BusFactory.newInstance().createBus();
        GreetingService client = null;

        try {
            ClientProxyFactoryBean clientFactory = new ClientProxyFactoryBean();
            clientFactory.setBus(bus);
            clientFactory.setServiceClass(GreetingService.class);
            clientFactory.setAddress("local://client-proxy-" + UUID.randomUUID());
            clientFactory.getServiceFactory().setWrapped(false);
            client = clientFactory.create(GreetingService.class);

            assertThat(client).isInstanceOf(Closeable.class);
            assertThat(ClientProxy.getClient(client)).isNotNull();

            ((Closeable) client).close();

            assertThat(ClientProxy.getClient(client)).isNull();
        } finally {
            if (client != null && ClientProxy.getClient(client) != null) {
                ((Closeable) client).close();
            }
            bus.shutdown(true);
        }
    }

    public interface GreetingService {
        String greet(String name);
    }
}
