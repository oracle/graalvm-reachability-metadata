/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.AbstractRelNode;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.metadata.BuiltInMetadata;
import org.apache.calcite.rel.metadata.JaninoRelMetadataProvider;
import org.apache.calcite.rel.metadata.MetadataHandlerProvider;
import org.apache.calcite.rel.metadata.RelMdRowCount;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.sql.type.SqlTypeName;
import org.junit.jupiter.api.Test;

public class JaninoRelMetadataProviderTest {
    @Test
    void metadataQueryCompilesRowCountHandlerAfterInitialProxyMiss() {
        JaninoRelMetadataProvider.clearStaticCache();
        JaninoRelMetadataProvider provider = JaninoRelMetadataProvider.of(RelMdRowCount.SOURCE);
        RelMetadataQuery query = new RelMetadataQuery(provider);

        Double rowCount = query.getRowCount(new TestRelNode());

        assertThat(rowCount).isEqualTo(1.0);
    }

    @Test
    void initialHandlerProxyReportsMissingHandlerForRelNodeClass() {
        JaninoRelMetadataProvider provider = JaninoRelMetadataProvider.of(RelMdRowCount.SOURCE);
        BuiltInMetadata.RowCount.Handler handler = provider.handler(
                BuiltInMetadata.RowCount.Handler.class);
        RelNode rel = new TestRelNode();
        RelMetadataQuery query = new RelMetadataQuery(provider);

        assertThatThrownBy(() -> handler.getRowCount(rel, query))
                .isInstanceOf(MetadataHandlerProvider.NoHandler.class);
    }

    private static class TestRelNode extends AbstractRelNode {
        TestRelNode() {
            this(createCluster());
        }

        private TestRelNode(RelOptCluster cluster) {
            super(cluster, cluster.traitSet());
        }

        private static RelOptCluster createCluster() {
            JavaTypeFactoryImpl typeFactory = new JavaTypeFactoryImpl();
            RexBuilder rexBuilder = new RexBuilder(typeFactory);
            return RelOptCluster.create(new VolcanoPlanner(), rexBuilder);
        }

        @Override
        protected RelDataType deriveRowType() {
            return getCluster().getTypeFactory().builder()
                    .add("VALUE", SqlTypeName.INTEGER)
                    .build();
        }
    }
}
