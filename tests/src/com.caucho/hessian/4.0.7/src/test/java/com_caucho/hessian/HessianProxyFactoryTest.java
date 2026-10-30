/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.client.HessianConnectionFactory;
import com.caucho.hessian.client.HessianProxyFactory;
import com.caucho.hessian.client.HessianURLConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.ResourceLock;

import javax.naming.Reference;
import javax.naming.StringRefAddr;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(30)
public class HessianProxyFactoryTest {
    @Test
    void invokesTypedAndDiscoveredServices() throws Exception {
        try (RpcEndpoint endpoint = RpcEndpoint.hessian()) {
            HessianProxyFactory factory = factory();
            RpcEndpoint.Greeting typed = (RpcEndpoint.Greeting) factory.create(RpcEndpoint.Greeting.class, endpoint.url());
            assertEquals("Hello, Ada", typed.greet("Ada"));
            RpcEndpoint.Greeting discovered = (RpcEndpoint.Greeting) factory.create(endpoint.url());
            assertEquals("Hello, Grace", discovered.greet("Grace"));
        }
    }

    @Test
    void createsServiceFromJndiReference() throws Exception {
        try (RpcEndpoint endpoint = RpcEndpoint.hessian()) {
            Reference reference = new Reference(RpcEndpoint.Greeting.class.getName());
            reference.add(new StringRefAddr("type", RpcEndpoint.Greeting.class.getName()));
            reference.add(new StringRefAddr("url", endpoint.url()));
            RpcEndpoint.Greeting service = (RpcEndpoint.Greeting) factory().getObjectInstance(reference, null, null, null);
            assertEquals("Hello, Lin", service.greet("Lin"));
        }
    }

    @Test
    @ResourceLock("java.lang.System.properties")
    void loadsConfiguredConnectionFactory() throws Exception {
        String key = HessianConnectionFactory.class.getName();
        String previous = System.getProperty(key);
        System.setProperty(key, HessianURLConnectionFactory.class.getName());
        try (RpcEndpoint endpoint = RpcEndpoint.hessian()) {
            RpcEndpoint.Greeting service = (RpcEndpoint.Greeting) factory().create(RpcEndpoint.Greeting.class, endpoint.url());
            assertEquals("Hello, Robin", service.greet("Robin"));
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    private HessianProxyFactory factory() {
        HessianProxyFactory factory = new HessianProxyFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(10000);
        return factory;
    }
}
