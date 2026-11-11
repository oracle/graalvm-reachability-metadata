/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.server.HessianSkeleton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class AbstractSkeletonTest {
    @Test
    void advertisesServiceApiAndDispatchesIt() throws Exception {
        HessianSkeleton skeleton = new HessianSkeleton(new RpcSupport.EchoService(), RpcSupport.Echo.class);
        assertEquals(RpcSupport.Echo.class.getName(), skeleton.getAPIClassName());
        try (RpcSupport server = new RpcSupport()) {
            RpcSupport.Echo proxy = (RpcSupport.Echo) RpcSupport.hessianFactory().create(server.url(false));
            assertEquals("reply:metadata", proxy.echo("metadata"));
        }
    }
}
