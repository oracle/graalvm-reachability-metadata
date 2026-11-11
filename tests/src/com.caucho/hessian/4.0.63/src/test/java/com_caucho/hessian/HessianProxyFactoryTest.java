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
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class HessianProxyFactoryTest {
    @Test
    @ResourceLock("java.lang.System.properties")
    void invokesTypedDiscoveredAndJndiProxies() throws Exception {
        String key = HessianConnectionFactory.class.getName();
        String previous = System.getProperty(key);
        System.setProperty(key, HessianURLConnectionFactory.class.getName());
        try (RpcSupport server = new RpcSupport()) {
            HessianProxyFactory factory = RpcSupport.hessianFactory();
            RpcSupport.Echo typed = (RpcSupport.Echo) factory.create(RpcSupport.Echo.class, server.url(false));
            assertEquals("reply:typed", typed.echo("typed"));
            RpcSupport.Echo discovered = (RpcSupport.Echo) factory.create(server.url(false));
            assertEquals("reply:discovered", discovered.echo("discovered"));
            Reference reference = new Reference(RpcSupport.Echo.class.getName());
            reference.add(new StringRefAddr("type", RpcSupport.Echo.class.getName()));
            reference.add(new StringRefAddr("url", server.url(false)));
            RpcSupport.Echo jndi = (RpcSupport.Echo) factory.getObjectInstance(reference, null, null, null);
            assertEquals("reply:jndi", jndi.echo("jndi"));
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }
}
