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
import org.apache.calcite.adapter.enumerable.EnumerableRel;
import org.apache.calcite.adapter.enumerable.EnumerableRules;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.interpreter.Interpreters;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.RelBuilder;
import org.junit.jupiter.api.Test;

public class EnumerableInterpretableTest {
    @Test
    void bindableEnumerableValuesCompilesAndInstantiatesGeneratedClass() {
        RelBuilder builder = newRelBuilder();
        RelNode logicalValues = builder
                .values(new String[] {"DEPARTMENT_ID", "SCORE"},
                        10, 8,
                        20, 13)
                .build();
        EnumerableRel enumerableValues = (EnumerableRel) EnumerableRules.ENUMERABLE_VALUES_RULE
                .convert(logicalValues);
        RelNode bindableRel = EnumerableRules.TO_BINDABLE.convert(enumerableValues);

        List<Object[]> rows = Interpreters.bindable(bindableRel)
                .bind(new TypeFactoryDataContext((JavaTypeFactory) builder.getTypeFactory()))
                .toList();

        assertThat(rows)
                .extracting(row -> List.of(row[0], row[1]))
                .containsExactly(List.of(10, 8), List.of(20, 13));
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
