/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.model.ModelHandler;
import org.apache.calcite.schema.Function;
import org.apache.calcite.schema.ScalarFunction;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.tools.Frameworks;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelHandlerTest {
    @Test
    public void addFunctionsLoadsClassNameAndRegistersScalarFunction() {
        SchemaPlus schema = Frameworks.createRootSchema(true);

        ModelHandler.addFunctions(
            schema,
            null,
            Collections.emptyList(),
            ModelFunctions.class.getName(),
            "triple",
            true);

        Collection<Function> functions = schema.getFunctions("TRIPLE");
        assertThat(functions).hasSize(1);
        assertThat(functions.iterator().next()).isInstanceOf(ScalarFunction.class);
    }

    public static final class ModelFunctions {
        private ModelFunctions() {
        }

        public static int triple(int value) {
            return value * 3;
        }
    }
}
