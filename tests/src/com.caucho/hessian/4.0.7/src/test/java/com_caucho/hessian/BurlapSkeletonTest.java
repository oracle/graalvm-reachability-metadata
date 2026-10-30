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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BurlapSkeletonTest {
    @Test
    void dispatchesEncodedRequestAndWritesReply() throws Throwable {
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        BurlapOutput output = new BurlapOutput(request);
        output.call("greet", new Object[] {"Morgan"});
        output.flush();
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        BurlapOutput reply = new BurlapOutput(response);
        BurlapSkeleton skeleton = new BurlapSkeleton(new RpcEndpoint.GreetingService(), RpcEndpoint.Greeting.class);
        skeleton.invoke(new BurlapInput(new ByteArrayInputStream(request.toByteArray())), reply);
        reply.flush();
        BurlapInput input = new BurlapInput(new ByteArrayInputStream(response.toByteArray()));
        try {
            assertEquals("Hello, Morgan", input.readReply(String.class));
        } finally {
            input.close();
        }
    }
}
