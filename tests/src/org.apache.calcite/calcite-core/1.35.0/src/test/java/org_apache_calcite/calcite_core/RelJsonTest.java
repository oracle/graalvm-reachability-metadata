/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelDistribution;
import org.apache.calcite.rel.RelInput;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.Collect;
import org.apache.calcite.rel.externalize.RelJson;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.util.ImmutableBitSet;

import com.google.common.collect.ImmutableList;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RelJsonTest {
    @Test
    public void resolvesShortAndFullyQualifiedTypeNames() {
        RelJson relJson = RelJson.create();

        assertThat(relJson.typeNameToClass("LogicalValues"))
            .isEqualTo(LogicalValues.class);
        assertThat(relJson.typeNameToClass(LogicalValues.class.getName()))
            .isEqualTo(LogicalValues.class);
    }

    @Test
    public void createsRelNodeFromSerializedInputMap() {
        RelInputMap input = new RelInputMap(cluster());
        input.put("type", "Collect");
        input.put("field", "COLLECTED_ROWS");

        RelNode relNode = RelJson.create().create(input);

        assertThat(relNode).isInstanceOf(Collect.class);
        assertThat(relNode.getRowType().getFieldNames()).containsExactly("COLLECTED_ROWS");
    }

    private static RelOptCluster cluster() {
        VolcanoPlanner planner = new VolcanoPlanner();
        RexBuilder rexBuilder = new RexBuilder(new JavaTypeFactoryImpl());
        return RelOptCluster.create(planner, rexBuilder);
    }

    private static final class RelInputMap extends HashMap<String, Object> implements RelInput {
        private final RelOptCluster cluster;
        private final RelNode input;

        private RelInputMap(RelOptCluster cluster) {
            this.cluster = cluster;
            this.input = LogicalValues.createOneRow(cluster);
        }

        @Override
        public RelOptCluster getCluster() {
            return cluster;
        }

        @Override
        public RelTraitSet getTraitSet() {
            return cluster.traitSetOf(Convention.NONE);
        }

        @Override
        public RelOptTable getTable(String table) {
            throw new UnsupportedOperationException("Tables are not used by this test input");
        }

        @Override
        public RelNode getInput() {
            return input;
        }

        @Override
        public List<RelNode> getInputs() {
            return Collections.singletonList(input);
        }

        @Override
        public RexNode getExpression(String tag) {
            throw new UnsupportedOperationException("Expressions are not used by this test input");
        }

        @Override
        public ImmutableBitSet getBitSet(String tag) {
            throw new UnsupportedOperationException("Bit sets are not used by this test input");
        }

        @Override
        public List<ImmutableBitSet> getBitSetList(String tag) {
            throw new UnsupportedOperationException("Bit set lists are not used by this test input");
        }

        @Override
        public List<AggregateCall> getAggregateCalls(String tag) {
            throw new UnsupportedOperationException("Aggregate calls are not used by this test input");
        }

        @Override
        public Object get(String tag) {
            return super.get(tag);
        }

        @Override
        public String getString(String tag) {
            return (String) get(tag);
        }

        @Override
        public float getFloat(String tag) {
            return ((Number) get(tag)).floatValue();
        }

        @Override
        public <E extends Enum<E>> E getEnum(String tag, Class<E> enumClass) {
            throw new UnsupportedOperationException("Enums are not used by this test input");
        }

        @Override
        public List<RexNode> getExpressionList(String tag) {
            throw new UnsupportedOperationException("Expression lists are not used by this test input");
        }

        @Override
        public List<String> getStringList(String tag) {
            throw new UnsupportedOperationException("String lists are not used by this test input");
        }

        @Override
        public List<Integer> getIntegerList(String tag) {
            throw new UnsupportedOperationException("Integer lists are not used by this test input");
        }

        @Override
        public List<List<Integer>> getIntegerListList(String tag) {
            throw new UnsupportedOperationException("Integer lists are not used by this test input");
        }

        @Override
        public RelDataType getRowType(String tag) {
            throw new UnsupportedOperationException("Row types are not used by this test input");
        }

        @Override
        public RelDataType getRowType(String expressionsTag, String fieldsTag) {
            throw new UnsupportedOperationException("Row types are not used by this test input");
        }

        @Override
        public RelCollation getCollation() {
            throw new UnsupportedOperationException("Collations are not used by this test input");
        }

        @Override
        public RelDistribution getDistribution() {
            throw new UnsupportedOperationException("Distributions are not used by this test input");
        }

        @Override
        public ImmutableList<ImmutableList<RexLiteral>> getTuples(String tag) {
            throw new UnsupportedOperationException("Tuples are not used by this test input");
        }

        @Override
        public boolean getBoolean(String tag, boolean defaultValue) {
            Object value = get(tag);
            return value == null ? defaultValue : (Boolean) value;
        }
    }
}
