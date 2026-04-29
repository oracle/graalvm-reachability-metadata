/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.interpreter.Interpreters;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.RelBuilder;
import org.junit.jupiter.api.Test;

public class AggregateNodeInnerUdaAccumulatorFactoryTest {
    @Test
    void interpreterAggregateUsesReflectiveAccumulatorInstancesForStandardAggregates() {
        RelBuilder builder = newRelBuilder();
        RelNode aggregate = builder
                .values(new String[] {"DEPARTMENT_ID", "SCORE"},
                        1, 10,
                        1, 20,
                        2, 7)
                .aggregate(builder.groupKey(builder.field("DEPARTMENT_ID")),
                        builder.sum(false, "TOTAL_SCORE", builder.field("SCORE")),
                        builder.min("LOW_SCORE", builder.field("SCORE")),
                        builder.max("HIGH_SCORE", builder.field("SCORE")))
                .build();

        List<Object[]> rows = Interpreters.bindable(aggregate)
                .bind(new TypeFactoryDataContext((JavaTypeFactory) builder.getTypeFactory()))
                .toList();

        assertThat(rows)
                .extracting(row -> List.of(row[0], row[1], row[2], row[3]))
                .containsExactlyInAnyOrder(
                        List.of(1, 30, 10, 20),
                        List.of(2, 7, 7, 7));
    }

    private static RelBuilder newRelBuilder() {
        FrameworkConfig config = Frameworks.newConfigBuilder()
                .defaultSchema(Frameworks.createRootSchema(true))
                .build();
        return RelBuilder.create(config);
    }

    private static class TypeFactoryDataContext implements DataContext {
        private final JavaTypeFactory typeFactory;

        TypeFactoryDataContext(JavaTypeFactory typeFactory) {
            this.typeFactory = typeFactory;
        }

        @Override
        public SchemaPlus getRootSchema() {
            return null;
        }

        @Override
        public JavaTypeFactory getTypeFactory() {
            return typeFactory;
        }

        @Override
        public QueryProvider getQueryProvider() {
            return null;
        }

        @Override
        public Object get(String name) {
            return null;
        }
    }
}
