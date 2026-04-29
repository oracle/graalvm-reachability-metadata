/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.jdbc.Driver;
import org.apache.calcite.schema.FunctionContext;
import org.apache.calcite.schema.ScalarFunction;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Objects;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class RexToLixTranslatorTest {
    @Test
    public void scalarFunctionUsesFunctionContextConstructorDuringEnumerableTranslation()
        throws Exception {
        try (Connection connection = openConnection()) {
            SchemaPlus schema = addRowsSchema(connection);
            schema.add("CONTEXT_LABEL", scalarFunction(ContextLabelFunction.class));

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("""
                     select "CONTEXT_LABEL"('tag', "NAME") as "LABEL"
                     from "ROWS"
                     """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("LABEL"))
                    .isEqualTo("tag:calcite:2:true:false");
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

    private static Connection openConnection() throws Exception {
        return Objects.requireNonNull(
            new Driver().connect("jdbc:calcite:", new Properties()),
            "Calcite JDBC driver did not create a connection");
    }

    private static SchemaPlus addRowsSchema(Connection connection) throws Exception {
        CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class);
        SchemaPlus schema = calciteConnection.getRootSchema()
            .add("TEST_DATA", new ReflectiveSchema(new RowsSchema()));
        calciteConnection.setSchema("TEST_DATA");
        return schema;
    }

    private static ScalarFunction scalarFunction(Class<?> functionClass) {
        return Objects.requireNonNull(
            ScalarFunctionImpl.create(functionClass, "eval"),
            () -> functionClass.getName() + " must be recognized as a scalar function");
    }

    public static final class RowsSchema {
        public final InputRow[] ROWS = {
            new InputRow("calcite")
        };
    }

    public static final class InputRow {
        public final String NAME;

        public InputRow(String name) {
            this.NAME = name;
        }
    }

    public static final class ContextLabelFunction {
        private final int parameterCount;
        private final boolean prefixConstant;
        private final boolean valueConstant;
        private final String capturedPrefix;

        public ContextLabelFunction(FunctionContext context) {
            this.parameterCount = context.getParameterCount();
            this.prefixConstant = context.isArgumentConstant(0);
            this.valueConstant = context.isArgumentConstant(1);
            this.capturedPrefix = context.getArgumentValueAs(0, String.class);
        }

        public String eval(String prefix, String value) {
            return capturedPrefix + ":" + value + ":" + parameterCount + ":"
                + prefixConstant + ":" + valueConstant;
        }
    }
}
