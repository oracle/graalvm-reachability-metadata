/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.client.HessianProxyFactory;
import com.caucho.hessian.io.HessianRemote;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class RemoteSerializerTest {
    @Test
    void encodesRemoteReferenceTypeAndUrl() throws Exception {
        String url = "http://localhost/snapshots";
        Object proxy = new HessianProxyFactory().create(RpcSupport.Echo.class, url);
        HessianRemote restored = (HessianRemote) SerializationSupport.roundTrip(proxy);
        assertEquals(RpcSupport.Echo.class.getName(), restored.getType());
        assertEquals(url, restored.getURL());
    }
}
