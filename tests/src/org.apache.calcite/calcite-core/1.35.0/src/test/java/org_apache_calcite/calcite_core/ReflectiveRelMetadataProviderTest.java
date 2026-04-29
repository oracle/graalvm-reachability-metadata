/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.rel.metadata.BuiltInMetadata;
import org.apache.calcite.rel.metadata.RelMdRowCount;
import org.apache.calcite.rel.metadata.RelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.metadata.UnboundMetadata;
import org.apache.calcite.rex.RexBuilder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveRelMetadataProviderTest {
    @Test
    @SuppressWarnings("deprecation")
    public void bindsReflectiveMetadataAndInvokesHandlerMethod() {
        RelMetadataProvider provider = RelMdRowCount.SOURCE;
        RelNode rel = relNode();

        UnboundMetadata<BuiltInMetadata.RowCount> rowCountFactory = provider.apply(
            rel.getClass(), BuiltInMetadata.RowCount.class);

        assertThat(rowCountFactory).isNotNull();
        BuiltInMetadata.RowCount rowCount = rowCountFactory.bind(rel, RelMetadataQuery.instance());

        assertThat(rowCount.rel()).isSameAs(rel);
        assertThat(rowCount.getRowCount()).isEqualTo(1.0d);
    }

    private static RelNode relNode() {
        VolcanoPlanner planner = new VolcanoPlanner();
        RexBuilder rexBuilder = new RexBuilder(new JavaTypeFactoryImpl());
        RelOptCluster cluster = RelOptCluster.create(planner, rexBuilder);
        return LogicalValues.createOneRow(cluster);
    }
}
