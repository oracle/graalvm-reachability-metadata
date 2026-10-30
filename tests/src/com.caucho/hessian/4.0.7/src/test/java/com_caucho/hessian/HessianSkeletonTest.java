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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HessianSkeletonTest {
    @Test
    void dispatchesEncodedRequestAndWritesReply() throws Throwable {
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        HessianOutput output = new HessianOutput(request);
        output.call("greet", new Object[] {"Morgan"});
        output.flush();
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        HessianSkeleton skeleton = new HessianSkeleton(new RpcEndpoint.GreetingService(), RpcEndpoint.Greeting.class);
        skeleton.invoke(new ByteArrayInputStream(request.toByteArray()), response);
        HessianInput input = new HessianInput(new ByteArrayInputStream(response.toByteArray()));
        try {
            assertEquals("Hello, Morgan", input.readReply(String.class));
        } finally {
            input.close();
        }
    }
}
