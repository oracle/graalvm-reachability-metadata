/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Collections;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.model.ModelHandler;
import org.apache.calcite.schema.Function;
import org.apache.calcite.schema.ScalarFunction;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.tools.Frameworks;
import org.junit.jupiter.api.Test;

public class ModelHandlerTest {
    @Test
    void addFunctionsLoadsScalarFunctionClassByName() {
        SchemaPlus schema = Frameworks.createRootSchema(true);

        ModelHandler.addFunctions(
                schema,
                "PLUS_ONE",
                Collections.emptyList(),
                ModelFunctions.class.getName(),
                "plusOne",
                false);

        Collection<Function> functions = schema.getFunctions("PLUS_ONE");

        assertThat(functions).hasSize(1);
        Function function = functions.iterator().next();
        assertThat(function).isInstanceOf(ScalarFunction.class);
        ScalarFunction scalarFunction = (ScalarFunction) function;
        assertThat(scalarFunction.getParameters()).hasSize(1);
        assertThat(scalarFunction.getReturnType(new JavaTypeFactoryImpl()).getSqlTypeName())
                .isEqualTo(SqlTypeName.INTEGER);
    }

    public static class ModelFunctions {
        public ModelFunctions() {
        }

        public static int plusOne(int value) {
            return value + 1;
        }
    }
}
