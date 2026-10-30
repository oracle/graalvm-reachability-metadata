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

public class AbstractSkeletonTest {
    @Test
    void includesInheritedApiMethodsInDispatchTable() throws Throwable {
        HessianSkeleton skeleton = new HessianSkeleton(new Service(), ExtendedApi.class);
        assertEquals(ExtendedApi.class.getName(), skeleton.getAPIClassName());
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        HessianOutput output = new HessianOutput(request);
        output.call("label", new Object[] {17});
        output.flush();
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        skeleton.invoke(new ByteArrayInputStream(request.toByteArray()), response);
        HessianInput input = new HessianInput(new ByteArrayInputStream(response.toByteArray()));
        try {
            assertEquals("item-17", input.readReply(String.class));
        } finally {
            input.close();
        }
    }

    public interface BaseApi {
        String label(int id);
    }

    public interface ExtendedApi extends BaseApi {
        String description();
    }

    public static class Service implements ExtendedApi {
        @Override
        public String label(int id) {
            return "item-" + id;
        }

        @Override
        public String description() {
            return "catalog";
        }
    }
}
