/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.model.ModelHandler;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.tools.Frameworks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelHandlerTest {
    @Test
    void addsScalarFunctionByClassName() {
        SchemaPlus schema = Frameworks.createRootSchema(true);

        ModelHandler.addFunctions(
                schema, "doubleValue", List.of(), ArithmeticFunction.class.getName(), "eval", false);

        assertThat(schema.getFunctions("doubleValue")).hasSize(1);
    }

    public static class ArithmeticFunction {
        public static int eval(int value) {
            return value * 2;
        }
    }
}
