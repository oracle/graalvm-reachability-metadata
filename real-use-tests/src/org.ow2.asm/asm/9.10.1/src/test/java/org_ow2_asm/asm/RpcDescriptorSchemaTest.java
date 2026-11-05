/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Builds RPC endpoint schemas from JVM descriptors for an application's dispatch subsystem. */
public class RpcDescriptorSchemaTest {
    @Test
    @Timeout(60)
    void describesEndpointParametersAndResult() {
        EndpointSchema schema = EndpointSchema.fromDescriptor(
                "(Ljava/lang/String;[I)Ljava/util/Optional;");

        assertEquals(List.of("java.lang.String", "int[]"), schema.parameterTypes());
        assertEquals("java.util.Optional", schema.resultType());
    }

    private record EndpointSchema(List<String> parameterTypes, String resultType) {
        private static EndpointSchema fromDescriptor(String descriptor) {
            Type method = Type.getMethodType(descriptor);
            List<String> parameters = new ArrayList<>();
            for (Type argument : method.getArgumentTypes()) {
                parameters.add(argument.getClassName());
            }
            return new EndpointSchema(parameters, method.getReturnType().getClassName());
        }
    }
}
