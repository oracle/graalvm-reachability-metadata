/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.burlap.client.BurlapProxyFactory;
import com.caucho.burlap.io.BurlapRemoteObject;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class BurlapProxyFactoryTest {
    @Test
    void invokesTypedDiscoveredAndJndiProxies() throws Exception {
        try (RpcSupport server = new RpcSupport()) {
            BurlapProxyFactory factory = RpcSupport.burlapFactory();
            RpcSupport.Echo typed = (RpcSupport.Echo) factory.create(RpcSupport.Echo.class, server.url(true));
            assertEquals("reply:typed", typed.echo("typed"));
            assertEquals(server.url(true), ((BurlapRemoteObject) typed).getBurlapURL());
            RpcSupport.Echo discovered = (RpcSupport.Echo) factory.create(server.url(true));
            assertEquals("reply:discovered", discovered.echo("discovered"));
            Reference reference = new Reference(RpcSupport.Echo.class.getName());
            reference.add(new StringRefAddr("type", RpcSupport.Echo.class.getName()));
            reference.add(new StringRefAddr("url", server.url(true)));
            RpcSupport.Echo jndi = (RpcSupport.Echo) factory.getObjectInstance(reference, null, null, null);
            assertEquals("reply:jndi", jndi.echo("jndi"));
        }
    }
}
