/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.DataContexts;
import org.apache.calcite.interpreter.Interpreter;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.jdbc.Driver;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.RelBuilder;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class AggregateNodeInnerUdaAccumulatorTest {
    @Test
    void accumulatesRowsAndReturnsInterpretedSum() throws Exception {
        RelBuilder builder = RelBuilder.create(Frameworks.newConfigBuilder().build());
        builder.values(new String[] {"v"}, 4, 9, 2);
        builder.aggregate(
                builder.groupKey(), builder.sum(false, "total", builder.field("v")));
        RelNode aggregate = builder.build();

        try (Connection connection = new Driver().connect("jdbc:calcite:", new Properties())) {
            CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class);
            SchemaPlus rootSchema = calciteConnection.getRootSchema();
            try (Interpreter interpreter = new Interpreter(
                    DataContexts.of(calciteConnection, rootSchema), aggregate)) {
                List<Object[]> rows = interpreter.toList();

                assertThat(rows).hasSize(1);
                assertThat(rows.get(0)).containsExactly(15);
            }
        }
    }
}
