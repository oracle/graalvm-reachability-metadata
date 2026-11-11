/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;
import com.caucho.hessian.server.HessianSkeleton;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class HessianSkeletonTest {
    @Test
    void dispatchesEncodedMethodCall() throws Throwable {
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        HessianOutput output = new HessianOutput(request);
        output.call("echo", new Object[] {"snapshot"});
        output.flush();
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        new HessianSkeleton(new RpcSupport.EchoService(), RpcSupport.Echo.class)
                .invoke(new ByteArrayInputStream(request.toByteArray()), response);
        HessianInput input = new HessianInput(new ByteArrayInputStream(response.toByteArray()));
        assertEquals("reply:snapshot", input.readReply(String.class));
        input.close();
        output.close();
    }
}
