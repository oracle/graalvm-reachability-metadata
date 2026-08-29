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
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelDistribution;
import org.apache.calcite.rel.RelDistributions;
import org.apache.calcite.rel.RelInput;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelTraitSet;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.externalize.RelJson;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.JsonBuilder;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RelJsonTest {
    @Test
    void resolvesShortAndQualifiedRelationTypeNames() {
        RelJson relJson = new RelJson(new JsonBuilder());

        assertThat(relJson.typeNameToClass("LogicalValues")).isEqualTo(LogicalValues.class);
        assertThat(relJson.typeNameToClass(LogicalProject.class.getName())).isEqualTo(LogicalProject.class);
        assertThat(relJson.getConstructor("LogicalValues").getDeclaringClass())
                .isEqualTo(LogicalValues.class);
    }

    @Test
    void createsRelationFromMapBackedInput() {
        JavaTypeFactoryImpl typeFactory = new JavaTypeFactoryImpl();
        RexBuilder rexBuilder = new RexBuilder(typeFactory);
        RelOptCluster cluster = RelOptCluster.create(new VolcanoPlanner(), rexBuilder);
        RelDataType integerType = typeFactory.createSqlType(SqlTypeName.INTEGER);
        RelDataType rowType = typeFactory.builder().add("v", integerType).build();
        RexLiteral value = rexBuilder.makeExactLiteral(BigDecimal.valueOf(7), integerType);
        ImmutableList<ImmutableList<RexLiteral>> tuples =
                ImmutableList.of(ImmutableList.of(value));
        ValuesInput input = new ValuesInput(cluster, rowType, tuples);

        RelNode relation = RelJson.create().create(input);

        assertThat(relation).isInstanceOf(LogicalValues.class);
        assertThat(((LogicalValues) relation).getTuples()).isEqualTo(tuples);
    }

    private static class ValuesInput extends HashMap<String, Object> implements RelInput {
        private final RelOptCluster cluster;
        private final RelDataType rowType;
        private final ImmutableList<ImmutableList<RexLiteral>> tuples;

        private ValuesInput(
                RelOptCluster cluster,
                RelDataType rowType,
                ImmutableList<ImmutableList<RexLiteral>> tuples) {
            this.cluster = cluster;
            this.rowType = rowType;
            this.tuples = tuples;
            put("type", "LogicalValues");
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
            throw unsupported();
        }

        @Override
        public RelNode getInput() {
            throw unsupported();
        }

        @Override
        public List<RelNode> getInputs() {
            throw unsupported();
        }

        @Override
        public RexNode getExpression(String tag) {
            throw unsupported();
        }

        @Override
        public ImmutableBitSet getBitSet(String tag) {
            throw unsupported();
        }

        @Override
        public List<ImmutableBitSet> getBitSetList(String tag) {
            throw unsupported();
        }

        @Override
        public List<AggregateCall> getAggregateCalls(String tag) {
            throw unsupported();
        }

        @Override
        public Object get(String tag) {
            return super.get(tag);
        }

        @Override
        public String getString(String tag) {
            throw unsupported();
        }

        @Override
        public float getFloat(String tag) {
            throw unsupported();
        }

        @Override
        public <E extends Enum<E>> E getEnum(String tag, Class<E> enumClass) {
            throw unsupported();
        }

        @Override
        public List<RexNode> getExpressionList(String tag) {
            throw unsupported();
        }

        @Override
        public List<String> getStringList(String tag) {
            throw unsupported();
        }

        @Override
        public List<Integer> getIntegerList(String tag) {
            throw unsupported();
        }

        @Override
        public List<List<Integer>> getIntegerListList(String tag) {
            throw unsupported();
        }

        @Override
        public RelDataType getRowType(String tag) {
            return rowType;
        }

        @Override
        public RelDataType getRowType(String expressionsTag, String fieldsTag) {
            throw unsupported();
        }

        @Override
        public RelCollation getCollation() {
            return RelCollations.EMPTY;
        }

        @Override
        public RelDistribution getDistribution() {
            return RelDistributions.ANY;
        }

        @Override
        public ImmutableList<ImmutableList<RexLiteral>> getTuples(String tag) {
            return tuples;
        }

        @Override
        public boolean getBoolean(String tag, boolean defaultValue) {
            throw unsupported();
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("not required for values input");
        }
    }
}
