/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.burlap.client.BurlapProxyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class BurlapProxyResolverTest {
    @Test
    void resolvesAndInvokesRemoteService() throws Exception {
        try (RpcSupport server = new RpcSupport()) {
            BurlapProxyResolver resolver = new BurlapProxyResolver(RpcSupport.burlapFactory());
            RpcSupport.Echo proxy = (RpcSupport.Echo) resolver.lookup(RpcSupport.Echo.class.getName(), server.url(true));
            assertEquals("reply:resolved", proxy.echo("resolved"));
        }
    }
}
