/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptSchema;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.externalize.RelJsonReader;
import org.apache.calcite.rel.externalize.RelJsonWriter;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.tools.Frameworks;
import org.junit.jupiter.api.Test;

public class RelJsonReaderTest {
    @Test
    void readsLogicalValuesSerializedByRelJsonWriter() throws IOException {
        RelOptCluster cluster = createCluster();
        RelDataType rowType = cluster.getTypeFactory().builder()
                .add("ID", SqlTypeName.INTEGER)
                .add("NAME", SqlTypeName.VARCHAR)
                .build();
        LogicalValues original = LogicalValues.create(cluster, rowType, ImmutableList.of());
        RelJsonWriter writer = new RelJsonWriter();
        original.explain(writer);

        RelJsonReader reader = new RelJsonReader(
                cluster,
                new EmptyRelOptSchema(cluster.getTypeFactory()),
                Frameworks.createRootSchema(true));
        RelNode relNode = reader.read(writer.asString());

        assertThat(relNode).isInstanceOf(LogicalValues.class);
        LogicalValues values = (LogicalValues) relNode;
        assertThat(values.getRowType().getFieldNames()).containsExactly("ID", "NAME");
        assertThat(values.getTuples()).isEmpty();
    }

    private static RelOptCluster createCluster() {
        JavaTypeFactoryImpl typeFactory = new JavaTypeFactoryImpl();
        RexBuilder rexBuilder = new RexBuilder(typeFactory);
        return RelOptCluster.create(new VolcanoPlanner(), rexBuilder);
    }

    private static class EmptyRelOptSchema implements RelOptSchema {
        private final RelDataTypeFactory typeFactory;

        EmptyRelOptSchema(RelDataTypeFactory typeFactory) {
            this.typeFactory = typeFactory;
        }

        @Override
        public RelOptTable getTableForMember(List<String> names) {
            return null;
        }

        @Override
        public RelDataTypeFactory getTypeFactory() {
            return typeFactory;
        }

        @Override
        public void registerRules(RelOptPlanner planner) {
        }
    }
}
