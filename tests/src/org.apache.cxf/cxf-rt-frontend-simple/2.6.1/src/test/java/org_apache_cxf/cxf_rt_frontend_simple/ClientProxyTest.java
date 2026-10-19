/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_frontend_simple;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.junit.jupiter.api.Test;

public class ClientProxyTest {
    @Test
    public void configuresClientFactoryThroughPublicApi() {
        ClientProxyFactoryBean clientFactory = new ClientProxyFactoryBean();
        clientFactory.setServiceClass(GreetingService.class);
        clientFactory.setAddress("local://client-proxy");
        clientFactory.setUsername("client-user");
        clientFactory.setPassword("client-password");
        clientFactory.getServiceFactory().setWrapped(false);

        assertThat(clientFactory.getServiceClass()).isEqualTo(GreetingService.class);
        assertThat(clientFactory.getAddress()).isEqualTo("local://client-proxy");
        assertThat(clientFactory.getUsername()).isEqualTo("client-user");
        assertThat(clientFactory.getPassword()).isEqualTo("client-password");
        assertThat(clientFactory.getServiceFactory().isWrapped()).isFalse();
    }

    public interface GreetingService {
        String greet(String name);
    }
}
