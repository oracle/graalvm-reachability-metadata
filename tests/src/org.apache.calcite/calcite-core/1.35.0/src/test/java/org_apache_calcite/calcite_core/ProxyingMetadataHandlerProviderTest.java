/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.List;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.AbstractRelNode;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.metadata.BuiltInMetadata;
import org.apache.calcite.rel.metadata.Metadata;
import org.apache.calcite.rel.metadata.MetadataDef;
import org.apache.calcite.rel.metadata.MetadataHandler;
import org.apache.calcite.rel.metadata.ProxyingMetadataHandlerProvider;
import org.apache.calcite.rel.metadata.RelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.metadata.UnboundMetadata;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.sql.type.SqlTypeName;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

public class ProxyingMetadataHandlerProviderTest {
    @Test
    void handlerDelegatesThroughProxyToBoundMetadata() {
        ProxyingMetadataHandlerProvider provider = new ProxyingMetadataHandlerProvider(
                new FixedRowCountProvider(42.0));
        BuiltInMetadata.RowCount.Handler handler = provider.handler(BuiltInMetadata.RowCount.Handler.class);
        RelNode rel = new TestRelNode();

        Double rowCount = handler.getRowCount(rel, RelMetadataQuery.instance());

        assertThat(rowCount).isEqualTo(42.0);
    }

    @Test
    void handlerReportsMissingMetadataForRelNodeClass() {
        ProxyingMetadataHandlerProvider provider = new ProxyingMetadataHandlerProvider(
                new MissingRowCountProvider());
        BuiltInMetadata.RowCount.Handler handler = provider.handler(BuiltInMetadata.RowCount.Handler.class);
        RelNode rel = new TestRelNode();

        assertThatThrownBy(() -> handler.getRowCount(rel, RelMetadataQuery.instance()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No handler for method")
                .hasMessageContaining(TestRelNode.class.getName());
    }

    private static class FixedRowCountProvider extends BaseMetadataProvider {
        private final double rowCount;

        FixedRowCountProvider(double rowCount) {
            this.rowCount = rowCount;
        }

        @Override
        public <M extends Metadata> UnboundMetadata<M> apply(
                Class<? extends RelNode> relClass, Class<? extends M> metadataClass) {
            if (metadataClass != BuiltInMetadata.RowCount.class) {
                return null;
            }
            return (rel, mq) -> metadataClass.cast(new FixedRowCountMetadata(rel, rowCount));
        }
    }

    private static class MissingRowCountProvider extends BaseMetadataProvider {
        @Override
        public <M extends Metadata> UnboundMetadata<M> apply(
                Class<? extends RelNode> relClass, Class<? extends M> metadataClass) {
            return null;
        }
    }

    private abstract static class BaseMetadataProvider implements RelMetadataProvider {
        @Override
        public <M extends Metadata> Multimap<Method, MetadataHandler<M>> handlers(MetadataDef<M> def) {
            return ImmutableMultimap.of();
        }

        @Override
        public List<MetadataHandler<?>> handlers(Class<? extends MetadataHandler<?>> handlerClass) {
            return ImmutableList.of();
        }
    }

    private static class FixedRowCountMetadata implements BuiltInMetadata.RowCount {
        private final RelNode rel;
        private final double rowCount;

        FixedRowCountMetadata(RelNode rel, double rowCount) {
            this.rel = rel;
            this.rowCount = rowCount;
        }

        @Override
        public RelNode rel() {
            return rel;
        }

        @Override
        public Double getRowCount() {
            return rowCount;
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
