/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.burlap.client.BurlapProxyFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.naming.Reference;
import javax.naming.StringRefAddr;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(30)
public class BurlapProxyFactoryTest {
    @Test
    void invokesTypedAndDiscoveredServices() throws Exception {
        try (RpcEndpoint endpoint = RpcEndpoint.burlap()) {
            BurlapProxyFactory factory = new RpcEndpoint.BoundedBurlapFactory();
            RpcEndpoint.Greeting typed = (RpcEndpoint.Greeting) factory.create(RpcEndpoint.Greeting.class, endpoint.url());
            assertEquals("Hello, Ada", typed.greet("Ada"));
            RpcEndpoint.Greeting discovered = (RpcEndpoint.Greeting) factory.create(endpoint.url());
            assertEquals("Hello, Grace", discovered.greet("Grace"));
        }
    }

    @Test
    void createsServiceFromJndiReference() throws Exception {
        try (RpcEndpoint endpoint = RpcEndpoint.burlap()) {
            Reference reference = new Reference(RpcEndpoint.Greeting.class.getName());
            reference.add(new StringRefAddr("type", RpcEndpoint.Greeting.class.getName()));
            reference.add(new StringRefAddr("url", endpoint.url()));
            BurlapProxyFactory factory = new RpcEndpoint.BoundedBurlapFactory();
            RpcEndpoint.Greeting service = (RpcEndpoint.Greeting) factory.getObjectInstance(reference, null, null, null);
            assertEquals("Hello, Lin", service.greet("Lin"));
        }
    }
}
