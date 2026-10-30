/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.client.HessianProxyFactory;
import com.caucho.hessian.client.HessianProxyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(30)
public class HessianProxyResolverTest {
    @Test
    void resolvesRemoteInterfaceAndInvokesService() throws Exception {
        try (RpcEndpoint endpoint = RpcEndpoint.hessian()) {
            HessianProxyFactory factory = new HessianProxyFactory();
            factory.setConnectTimeout(10000);
            factory.setReadTimeout(10000);
            HessianProxyResolver resolver = new HessianProxyResolver(factory);
            RpcEndpoint.Greeting service = (RpcEndpoint.Greeting) resolver.lookup(RpcEndpoint.Greeting.class.getName(), endpoint.url());
            assertEquals("Hello, Sam", service.greet("Sam"));
        }
    }
}
