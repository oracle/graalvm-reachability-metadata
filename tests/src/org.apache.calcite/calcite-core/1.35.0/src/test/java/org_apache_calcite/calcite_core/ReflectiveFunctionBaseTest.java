/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.schema.FunctionContext;
import org.apache.calcite.schema.ScalarFunction;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveFunctionBaseTest {
    @Test
    void discoversFunctionsWithSupportedConstructors() {
        ScalarFunction zeroArgument = ScalarFunctionImpl.create(ZeroArgumentFunction.class, "eval");
        ScalarFunction context = ScalarFunctionImpl.create(ContextFunction.class, "eval");

        assertThat(zeroArgument).isNotNull();
        assertThat(context).isNotNull();
        assertThat(context.getParameters()).hasSize(1);
    }

    public static class ZeroArgumentFunction {
        public String eval(String value) {
            return value.toUpperCase();
        }
    }

    public static class ContextFunction {
        public ContextFunction(FunctionContext context) {
        }

        public String eval(String value) {
            return value.toLowerCase();
        }
    }
}
