/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.velocity.runtime.log.Log;
import io.sundr.deps.org.apache.velocity.runtime.log.NullLogChute;
import io.sundr.deps.org.apache.velocity.util.introspection.Info;
import io.sundr.deps.org.apache.velocity.util.introspection.UberspectImpl;
import io.sundr.deps.org.apache.velocity.util.introspection.VelMethod;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class UberspectImplInnerVelMethodImplTest {
    private static final Info INFO = new Info("uberspect-varargs-test", 1, 1);

    @Test
    void invokesVarargsMethodWithNoVariableArguments() throws Exception {
        VarargsTarget target = new VarargsTarget();
        Object[] arguments = new Object[] { "none" };
        VelMethod method = getMethod(target, arguments);

        Object result = method.invoke(target, arguments);

        assertThat(result).isEqualTo("none:0:");
    }

    @Test
    void invokesVarargsMethodWithSingleVariableArgument() throws Exception {
        VarargsTarget target = new VarargsTarget();
        Object[] arguments = new Object[] { "single", "alpha" };
        VelMethod method = getMethod(target, arguments);

        Object result = method.invoke(target, arguments);

        assertThat(result).isEqualTo("single:1:alpha");
    }

    @Test
    void invokesVarargsMethodWithMultipleVariableArguments() throws Exception {
        VarargsTarget target = new VarargsTarget();
        Object[] arguments = new Object[] { "many", "alpha", "beta" };
        VelMethod method = getMethod(target, arguments);

        Object result = method.invoke(target, arguments);

        assertThat(result).isEqualTo("many:2:alpha,beta");
    }

    private static VelMethod getMethod(VarargsTarget target, Object[] arguments) throws Exception {
        UberspectImpl uberspect = new UberspectImpl();
        uberspect.setLog(new Log(new NullLogChute()));
        uberspect.init();

        VelMethod method = uberspect.getMethod(target, "describe", arguments, INFO);

        assertThat(method).isNotNull();
        assertThat(method.getMethodName()).isEqualTo("describe");
        assertThat(method.getReturnType()).isSameAs(String.class);
        assertThat(method.isCacheable()).isTrue();
        return method;
    }

    public static class VarargsTarget {
        public String describe(String label, String... values) {
            String joinedValues = Arrays.stream(values).collect(Collectors.joining(","));
            return label + ":" + values.length + ":" + joinedValues;
        }
    }
}
