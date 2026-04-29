/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.AbstractRelNode;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.metadata.BuiltInMetadata;
import org.apache.calcite.rel.metadata.ChainedRelMetadataProvider;
import org.apache.calcite.rel.metadata.Metadata;
import org.apache.calcite.rel.metadata.MetadataDef;
import org.apache.calcite.rel.metadata.MetadataHandler;
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

@SuppressWarnings("deprecation")
public class ChainedRelMetadataProviderTest {
    @Test
    void bindCreatesProxyThatChecksProvidersUntilMetadataValueIsFound() {
        RowCountMetadata firstMetadata = new RowCountMetadata(null);
        RowCountMetadata secondMetadata = new RowCountMetadata(31.0);
        RelMetadataProvider provider = ChainedRelMetadataProvider.of(ImmutableList.of(
                new RowCountProvider(firstMetadata),
                new RowCountProvider(secondMetadata)));
        RelNode rel = new TestRelNode();

        UnboundMetadata<BuiltInMetadata.RowCount> unboundMetadata = provider.apply(
                TestRelNode.class, BuiltInMetadata.RowCount.class);

        assertThat(unboundMetadata).isNotNull();
        BuiltInMetadata.RowCount metadata = unboundMetadata.bind(rel, RelMetadataQuery.instance());

        assertThat(metadata.rel()).isSameAs(rel);
        assertThat(metadata.getRowCount()).isEqualTo(31.0);
        assertThat(firstMetadata.rowCountCalls).isEqualTo(1);
        assertThat(secondMetadata.rowCountCalls).isEqualTo(1);
    }

    private static class RowCountProvider extends BaseMetadataProvider {
        private final RowCountMetadata metadata;

        RowCountProvider(RowCountMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public <M extends Metadata> UnboundMetadata<M> apply(
                Class<? extends RelNode> relClass, Class<? extends M> metadataClass) {
            if (metadataClass != BuiltInMetadata.RowCount.class) {
                return null;
            }
            return (rel, mq) -> {
                metadata.rel = rel;
                return metadataClass.cast(metadata);
            };
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

    private static class RowCountMetadata implements BuiltInMetadata.RowCount {
        private final Double rowCount;
        private RelNode rel;
        private int rowCountCalls;

        RowCountMetadata(Double rowCount) {
            this.rowCount = rowCount;
        }

        @Override
        public RelNode rel() {
            return rel;
        }

        @Override
        public Double getRowCount() {
            rowCountCalls++;
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
