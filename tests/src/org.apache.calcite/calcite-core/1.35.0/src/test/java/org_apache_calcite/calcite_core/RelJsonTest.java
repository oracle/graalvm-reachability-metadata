/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelDistribution;
import org.apache.calcite.rel.RelDistributions;
import org.apache.calcite.rel.RelInput;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.externalize.RelJson;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.ImmutableBitSet;
import org.junit.jupiter.api.Test;

public class RelJsonTest {
    @Test
    void resolvesShortAndQualifiedRelTypeNames() {
        RelJson relJson = RelJson.create();

        assertThat(relJson.typeNameToClass("LogicalValues"))
                .isEqualTo(LogicalValues.class);
        assertThat(relJson.typeNameToClass(LogicalFilter.class.getName()))
                .isEqualTo(LogicalFilter.class);
    }

    @Test
    void discoversRelInputConstructorForShortTypeName() {
        RelJson relJson = RelJson.create();

        Constructor<?> constructor = relJson.getConstructor("LogicalValues");

        assertThat(constructor.getDeclaringClass()).isEqualTo(LogicalValues.class);
        assertThat(constructor.getParameterTypes()).containsExactly(RelInput.class);
    }

    @Test
    void createInstantiatesLogicalValuesFromRelInputBackedMap() {
        RelJson relJson = RelJson.create();
        ValuesRelInput input = new ValuesRelInput();

        RelNode relNode = relJson.create(input);

        assertThat(relNode).isInstanceOf(LogicalValues.class);
        assertThat(relNode.getRowType().getFieldNames()).containsExactly("VALUE");
        assertThat(((LogicalValues) relNode).getTuples()).isEmpty();
    }

    private static class ValuesRelInput extends HashMap<String, Object> implements RelInput {
        private final RelOptCluster cluster;
        private final RelDataType rowType;

        ValuesRelInput() {
            cluster = createCluster();
            rowType = cluster.getTypeFactory().builder()
                    .add("VALUE", SqlTypeName.INTEGER)
                    .build();
            put("type", "LogicalValues");
        }

        @Override
        public RelOptCluster getCluster() {
            return cluster;
        }

        @Override
        public RelTraitSet getTraitSet() {
            return cluster.traitSet();
        }

        @Override
        public RelOptTable getTable(String table) {
            throw new UnsupportedOperationException(table);
        }

        @Override
        public RelNode getInput() {
            throw new UnsupportedOperationException("input");
        }

        @Override
        public List<RelNode> getInputs() {
            return Collections.emptyList();
        }

        @Override
        public RexNode getExpression(String tag) {
            return null;
        }

        @Override
        public ImmutableBitSet getBitSet(String tag) {
            return ImmutableBitSet.of();
        }

        @Override
        public List<ImmutableBitSet> getBitSetList(String tag) {
            return Collections.emptyList();
        }

        @Override
        public List<AggregateCall> getAggregateCalls(String tag) {
            return Collections.emptyList();
        }

        @Override
        public Object get(String tag) {
            return super.get(tag);
        }

        @Override
        public String getString(String tag) {
            Object value = get(tag);
            return value instanceof String ? (String) value : null;
        }

        @Override
        public float getFloat(String tag) {
            return ((Number) get(tag)).floatValue();
        }

        @Override
        public <E extends Enum<E>> E getEnum(String tag, Class<E> enumClass) {
            return null;
        }

        @Override
        public List<RexNode> getExpressionList(String tag) {
            return Collections.emptyList();
        }

        @Override
        public List<String> getStringList(String tag) {
            return Collections.emptyList();
        }

        @Override
        public List<Integer> getIntegerList(String tag) {
            return Collections.emptyList();
        }

        @Override
        public List<List<Integer>> getIntegerListList(String tag) {
            return Collections.emptyList();
        }

        @Override
        public RelDataType getRowType(String tag) {
            return rowType;
        }

        @Override
        public RelDataType getRowType(String expressionsTag, String fieldsTag) {
            return rowType;
        }

        @Override
        public RelCollation getCollation() {
            return RelCollations.EMPTY;
        }

        @Override
        public RelDistribution getDistribution() {
            return RelDistributions.SINGLETON;
        }

        @Override
        public ImmutableList<ImmutableList<RexLiteral>> getTuples(String tag) {
            return ImmutableList.of();
        }

        @Override
        public boolean getBoolean(String tag, boolean defaultValue) {
            Object value = get(tag);
            return value instanceof Boolean ? (Boolean) value : defaultValue;
        }

        private static RelOptCluster createCluster() {
            JavaTypeFactoryImpl typeFactory = new JavaTypeFactoryImpl();
            RexBuilder rexBuilder = new RexBuilder(typeFactory);
            return RelOptCluster.create(new VolcanoPlanner(), rexBuilder);
        }
    }
}
