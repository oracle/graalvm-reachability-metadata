/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.AbstractRelNode;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.metadata.BuiltInMetadata;
import org.apache.calcite.rel.metadata.MetadataHandler;
import org.apache.calcite.rel.metadata.MetadataHandlerProvider;
import org.apache.calcite.rel.metadata.ReflectiveRelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.metadata.UnboundMetadata;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.sql.type.SqlTypeName;
import org.junit.jupiter.api.Test;

public class ReflectiveRelMetadataProviderTest {
    @Test
    void unboundMetadataCreatesProxyAndReflectivelyInvokesHandler() {
        FixedRowCountHandler handler = new FixedRowCountHandler(17.0);
        RelMetadataProvider provider = ReflectiveRelMetadataProvider.reflectiveSource(
                handler, BuiltInMetadata.RowCount.Handler.class);
        TestRelNode rel = new TestRelNode();
        UnboundMetadata<BuiltInMetadata.RowCount> unboundMetadata = provider.apply(
                TestRelNode.class, BuiltInMetadata.RowCount.class);

        assertThat(unboundMetadata).isNotNull();
        BuiltInMetadata.RowCount metadata = unboundMetadata.bind(
                rel, new RelMetadataQuery(new RowCountOnlyHandlerProvider(handler)));

        assertThat(metadata.rel()).isSameAs(rel);
        assertThat(metadata.getRowCount()).isEqualTo(17.0);
        assertThat(handler.lastRel).isSameAs(rel);
    }

    public static class FixedRowCountHandler implements BuiltInMetadata.RowCount.Handler {
        private final double rowCount;
        private RelNode lastRel;

        FixedRowCountHandler(double rowCount) {
            this.rowCount = rowCount;
        }

        @Override
        public Double getRowCount(RelNode rel, RelMetadataQuery mq) {
            lastRel = rel;
            return rowCount;
        }
    }

    private static class RowCountOnlyHandlerProvider implements MetadataHandlerProvider {
        private final FixedRowCountHandler rowCountHandler;

        RowCountOnlyHandlerProvider(FixedRowCountHandler rowCountHandler) {
            this.rowCountHandler = rowCountHandler;
        }

        @Override
        public <MH extends MetadataHandler<?>> MH handler(Class<MH> handlerClass) {
            if (handlerClass == BuiltInMetadata.RowCount.Handler.class) {
                return handlerClass.cast(rowCountHandler);
            }
            return null;
        }
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
