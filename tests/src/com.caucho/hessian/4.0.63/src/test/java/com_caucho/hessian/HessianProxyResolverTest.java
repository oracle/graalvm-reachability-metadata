/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.client.HessianProxyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class HessianProxyResolverTest {
    @Test
    void resolvesAndInvokesRemoteService() throws Exception {
        try (RpcSupport server = new RpcSupport()) {
            HessianProxyResolver resolver = new HessianProxyResolver(RpcSupport.hessianFactory());
            RpcSupport.Echo proxy = (RpcSupport.Echo) resolver.lookup(RpcSupport.Echo.class.getName(), server.url(false));
            assertEquals("reply:resolved", proxy.echo("resolved"));
        }
    }
}
