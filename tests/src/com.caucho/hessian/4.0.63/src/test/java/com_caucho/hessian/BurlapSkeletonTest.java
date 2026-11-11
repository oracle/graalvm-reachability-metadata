/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.burlap.io.BurlapInput;
import com.caucho.burlap.io.BurlapOutput;
import com.caucho.burlap.server.BurlapSkeleton;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class BurlapSkeletonTest {
    @Test
    void dispatchesEncodedMethodCall() throws Throwable {
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        BurlapOutput output = new BurlapOutput(request);
        output.call("echo", new Object[] {"snapshot"});
        output.flush();
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        BurlapOutput reply = new BurlapOutput(response);
        new BurlapSkeleton(new RpcSupport.EchoService(), RpcSupport.Echo.class).invoke(
                new BurlapInput(new ByteArrayInputStream(request.toByteArray())), reply);
        reply.flush();
        BurlapInput input = new BurlapInput(new ByteArrayInputStream(response.toByteArray()));
        assertEquals("reply:snapshot", input.readReply(String.class));
        input.close();
        reply.close();
        output.close();
    }
}
