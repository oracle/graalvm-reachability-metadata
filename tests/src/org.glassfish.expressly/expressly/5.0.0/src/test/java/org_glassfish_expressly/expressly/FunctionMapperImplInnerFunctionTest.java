/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_expressly.expressly;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;

import org.glassfish.expressly.lang.FunctionMapperImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionMapperImplInnerFunctionTest {

    @Test
    void resolvesMethodAfterExternalization() throws Exception {
        Method originalMethod = Math.class.getMethod("max", int.class, int.class);
        FunctionMapperImpl.Function originalFunction = new FunctionMapperImpl.Function("math", "max", originalMethod);

        byte[] externalizedFunction = externalize(originalFunction);
        FunctionMapperImpl.Function restoredFunction = new FunctionMapperImpl.Function();
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(externalizedFunction))) {
            restoredFunction.readExternal(input);
        }

        Method resolvedMethod = restoredFunction.getMethod();

        assertThat(resolvedMethod).isEqualTo(originalMethod);
        assertThat(resolvedMethod.getParameterTypes()).containsExactly(int.class, int.class);
    }

    private static byte[] externalize(FunctionMapperImpl.Function function) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            function.writeExternal(output);
        }
        return bytes.toByteArray();
    }
}
