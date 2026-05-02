/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_common;

import org.apache.parquet.util.DynMethods;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DynMethodsInnerUnboundMethodTest {
    @Test
    public void invokesFixedArityMethodWithExpectedArguments() throws Exception {
        MethodTargets target = new MethodTargets();
        DynMethods.UnboundMethod method = new DynMethods.Builder("fixedArityGreeting")
                .impl(MethodTargets.class, String.class)
                .build();

        String greeting = method.invokeChecked(target, "Ada", "ignored extra argument");

        assertThat(greeting).isEqualTo("hello Ada");
    }

    @Test
    public void invokesVarArgsMethodWithArrayArgument() throws Exception {
        MethodTargets target = new MethodTargets();
        DynMethods.UnboundMethod method = new DynMethods.Builder("varArgsGreeting")
                .impl(MethodTargets.class, String.class, String[].class)
                .build();

        String greeting = method.invokeChecked(target, "hello", new String[] {"Ada", "Lovelace"});

        assertThat(greeting).isEqualTo("hello Ada Lovelace");
    }

    public static final class MethodTargets {
        public String fixedArityGreeting(String name) {
            return "hello " + name;
        }

        public String varArgsGreeting(String prefix, String... names) {
            return prefix + " " + String.join(" ", names);
        }
    }
}
