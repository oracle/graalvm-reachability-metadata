/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.DataContexts;
import org.apache.calcite.adapter.enumerable.EnumerableInterpretable;
import org.apache.calcite.adapter.enumerable.EnumerableRel;
import org.apache.calcite.adapter.enumerable.EnumerableValues;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.plan.ConventionTraitDef;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.runtime.ArrayBindable;
import org.apache.calcite.runtime.Bindable;
import org.apache.calcite.sql.type.SqlTypeName;

import com.google.common.collect.ImmutableList;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumerableInterpretableTest {
    @Test
    public void compilesEnumerableValuesToBindableAndReadsRows() {
        RelOptCluster cluster = cluster();
        RelDataType rowType = rowType(cluster);
        EnumerableValues values = EnumerableValues.create(
            cluster, rowType, rows(cluster, rowType));

        Bindable<?> bindable = EnumerableInterpretable.toBindable(
            Collections.emptyMap(), null, values, EnumerableRel.Prefer.ARRAY);

        assertThat(bindable).isInstanceOf(ArrayBindable.class);
        Enumerator<Object[]> enumerator = ((ArrayBindable) bindable)
            .bind(DataContexts.EMPTY)
            .enumerator();
        try {
            assertThat(enumerator.moveNext()).isTrue();
            assertThat(enumerator.current())
                .containsExactly(BigDecimal.ONE, "Ada");
            assertThat(enumerator.moveNext()).isTrue();
            assertThat(enumerator.current())
                .containsExactly(BigDecimal.valueOf(2), "Grace");
            assertThat(enumerator.moveNext()).isFalse();
        } finally {
            enumerator.close();
        }
    }

    private static RelOptCluster cluster() {
        JavaTypeFactoryImpl typeFactory = new JavaTypeFactoryImpl();
        RexBuilder rexBuilder = new RexBuilder(typeFactory);
        VolcanoPlanner planner = new VolcanoPlanner();
        planner.addRelTraitDef(ConventionTraitDef.INSTANCE);
        return RelOptCluster.create(planner, rexBuilder);
    }

    private static RelDataType rowType(RelOptCluster cluster) {
        return cluster.getTypeFactory().builder()
            .add("ID", SqlTypeName.DECIMAL)
            .add("NAME", SqlTypeName.VARCHAR)
            .build();
    }

    private static ImmutableList<ImmutableList<RexLiteral>> rows(RelOptCluster cluster,
        RelDataType rowType) {
        List<RelDataTypeField> fields = rowType.getFieldList();
        RexBuilder rexBuilder = cluster.getRexBuilder();
        return ImmutableList.of(
            row(rexBuilder, fields, 1, "Ada"),
            row(rexBuilder, fields, 2, "Grace"));
    }

    private static ImmutableList<RexLiteral> row(RexBuilder rexBuilder,
        List<RelDataTypeField> fields, int id, String name) {
        return ImmutableList.of(
            rexBuilder.makeExactLiteral(
                BigDecimal.valueOf(id), fields.get(0).getType()),
            rexBuilder.makeLiteral(name, fields.get(1).getType()));
    }
}
