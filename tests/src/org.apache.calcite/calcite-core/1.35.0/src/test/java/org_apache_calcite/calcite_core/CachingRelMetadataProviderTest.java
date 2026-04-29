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
import org.apache.calcite.rel.metadata.CachingRelMetadataProvider;
import org.apache.calcite.rel.metadata.Metadata;
import org.apache.calcite.rel.metadata.MetadataDef;
import org.apache.calcite.rel.metadata.MetadataHandler;
import org.apache.calcite.rel.metadata.RelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.metadata.UnboundMetadata;
import org.apache.calcite.rex.RexBuilder;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import org.checkerframework.checker.nullness.qual.Nullable;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CachingRelMetadataProviderTest {
    @Test
    @SuppressWarnings("deprecation")
    public void bindsMetadataUsingCachingProxy() {
        RelNode rel = relNode();
        CountingMetadataProvider underlyingProvider = new CountingMetadataProvider(12.0);
        CachingRelMetadataProvider provider = new CachingRelMetadataProvider(
            underlyingProvider, rel.getCluster().getPlanner());

        UnboundMetadata<BuiltInMetadata.RowCount> rowCountFactory = provider.apply(
            rel.getClass(), BuiltInMetadata.RowCount.class);

        assertThat(rowCountFactory).isNotNull();
        BuiltInMetadata.RowCount rowCount = rowCountFactory.bind(rel, RelMetadataQuery.instance());
        assertThat(rowCount.getRowCount()).isEqualTo(12.0);
        assertThat(rowCount.getRowCount()).isEqualTo(12.0);
        assertThat(underlyingProvider.boundMetadata.invocationCount()).isEqualTo(1);
    }

    private static RelNode relNode() {
        VolcanoPlanner planner = new VolcanoPlanner();
        RexBuilder rexBuilder = new RexBuilder(new JavaTypeFactoryImpl());
        RelOptCluster cluster = RelOptCluster.create(planner, rexBuilder);
        return LogicalValues.createOneRow(cluster);
    }

    private static class CountingMetadataProvider implements RelMetadataProvider {
        private final double rowCount;
        private CountingRowCountMetadata boundMetadata;

        CountingMetadataProvider(double rowCount) {
            this.rowCount = rowCount;
        }

        @Deprecated
        @Override
        @SuppressWarnings("unchecked")
        public <@Nullable M extends @Nullable Metadata> @Nullable UnboundMetadata<M> apply(
            Class<? extends RelNode> relClass, Class<? extends M> metadataClass) {
            if (metadataClass == BuiltInMetadata.RowCount.class) {
                UnboundMetadata<BuiltInMetadata.RowCount> metadata = (rel, mq) -> {
                    boundMetadata = new CountingRowCountMetadata(rel, rowCount);
                    return boundMetadata;
                };
                return (UnboundMetadata<M>) metadata;
            }
            return null;
        }

        @Deprecated
        @Override
        public <M extends Metadata> Multimap<Method, MetadataHandler<M>> handlers(
            MetadataDef<M> def) {
            return ImmutableMultimap.of();
        }

        @Override
        public List<MetadataHandler<?>> handlers(
            Class<? extends MetadataHandler<?>> handlerClass) {
            return Collections.emptyList();
        }
    }

    public static class CountingRowCountMetadata implements BuiltInMetadata.RowCount {
        private final RelNode rel;
        private final double rowCount;
        private int invocationCount;

        CountingRowCountMetadata(RelNode rel, double rowCount) {
            this.rel = rel;
            this.rowCount = rowCount;
        }

        @Override
        public RelNode rel() {
            return rel;
        }

        @Override
        public Double getRowCount() {
            invocationCount++;
            return rowCount;
        }

        int invocationCount() {
            return invocationCount;
        }
    }
}
