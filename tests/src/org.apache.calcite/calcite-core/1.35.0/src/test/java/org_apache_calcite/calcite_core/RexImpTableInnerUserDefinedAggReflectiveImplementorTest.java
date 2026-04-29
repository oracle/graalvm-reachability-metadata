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
import org.apache.calcite.schema.Function;
import org.apache.calcite.schema.FunctionContext;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.impl.AggregateFunctionImpl;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Objects;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class RexImpTableInnerUserDefinedAggReflectiveImplementorTest {
    @Test
    public void enumerableAggregateCreatesUserDefinedAggregateByNoArgConstructor()
        throws Exception {
        try (Connection connection = openConnection()) {
            SchemaPlus schema = addRowsSchema(connection);
            schema.add("NO_ARG_SUM", aggregateFunction(NoArgSum.class));

            assertSingleIntegerResult(connection,
                "select \"NO_ARG_SUM\"(\"VALUE\") as \"TOTAL\" from \"ROWS\"",
                "TOTAL",
                6);
        }
    }

    @Test
    public void enumerableAggregateCreatesUserDefinedAggregateByFunctionContextConstructor()
        throws Exception {
        try (Connection connection = openConnection()) {
            SchemaPlus schema = addRowsSchema(connection);
            schema.add("CONTEXT_SUM", aggregateFunction(ContextAwareSum.class));

            assertSingleIntegerResult(connection,
                "select \"CONTEXT_SUM\"(\"VALUE\") as \"TOTAL\" from \"ROWS\"",
                "TOTAL",
                9);
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

    private static Function aggregateFunction(Class<?> aggregateClass) {
        return Objects.requireNonNull(
            AggregateFunctionImpl.create(aggregateClass),
            () -> aggregateClass.getName() + " must be recognized as an aggregate function");
    }

    private static void assertSingleIntegerResult(Connection connection, String sql,
        String columnLabel, int expectedValue) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(columnLabel)).isEqualTo(expectedValue);
            assertThat(resultSet.next()).isFalse();
        }
    }

    public static final class RowsSchema {
        public final InputRow[] ROWS = {
            new InputRow(1),
            new InputRow(2),
            new InputRow(3)
        };
    }

    public static final class InputRow {
        public final int VALUE;

        public InputRow(int value) {
            this.VALUE = value;
        }
    }

    public static final class NoArgSum {
        public NoArgSum() {
        }

        public int init() {
            return 0;
        }

        public int add(int accumulator, int value) {
            return accumulator + value;
        }

        public int result(int accumulator) {
            return accumulator;
        }
    }

    public static final class ContextAwareSum {
        private final int parameterCount;

        public ContextAwareSum(FunctionContext context) {
            this.parameterCount = context.getParameterCount();
        }

        public int init() {
            return 0;
        }

        public int add(int accumulator, int value) {
            return accumulator + value + parameterCount;
        }

        public int result(int accumulator) {
            return accumulator;
        }
    }
}
