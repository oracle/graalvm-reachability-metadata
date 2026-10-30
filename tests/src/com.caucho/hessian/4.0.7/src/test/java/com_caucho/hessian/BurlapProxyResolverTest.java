/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.burlap.client.BurlapProxyFactory;
import com.caucho.burlap.client.BurlapProxyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(30)
public class BurlapProxyResolverTest {
    @Test
    void resolvesRemoteInterfaceAndInvokesService() throws Exception {
        try (RpcEndpoint endpoint = RpcEndpoint.burlap()) {
            BurlapProxyFactory factory = new RpcEndpoint.BoundedBurlapFactory();
            BurlapProxyResolver resolver = new BurlapProxyResolver(factory);
            RpcEndpoint.Greeting service = (RpcEndpoint.Greeting) resolver.lookup(RpcEndpoint.Greeting.class.getName(), endpoint.url());
            assertEquals("Hello, Sam", service.greet("Sam"));
        }
    }
}
