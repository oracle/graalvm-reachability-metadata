/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.data.spel.spi.Function;

public class FunctionTest {

    @Test
    void invokesStaticMethodWithExactArguments() throws Exception {
        Method method = SampleFunctions.class.getMethod("greeting", String.class);
        Function function = new Function(method);

        Object result = function.invoke(new Object[] { "World" });

        assertThat(result).isEqualTo("Hello, World");
    }

    @Test
    void invokesInstanceMethodWithExpandedVarargs() throws Exception {
        SampleFunctions target = new SampleFunctions("-");
        Method method = SampleFunctions.class.getMethod("join", String[].class);
        Function function = new Function(method, target);

        Object result = function.invoke(new Object[] { "alpha", "bravo", "charlie" });

        assertThat(result).isEqualTo("alpha-bravo-charlie");
    }

    public static class SampleFunctions {

        private final String delimiter;

        SampleFunctions(String delimiter) {
            this.delimiter = delimiter;
        }

        public static String greeting(String name) {
            return "Hello, " + name;
        }

        public String join(String... values) {
            return String.join(delimiter, values);
        }
    }
}
