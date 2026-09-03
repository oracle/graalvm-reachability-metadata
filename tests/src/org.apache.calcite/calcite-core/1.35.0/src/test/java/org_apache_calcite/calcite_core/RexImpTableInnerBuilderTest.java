/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.adapter.enumerable.AggImplementor;
import org.apache.calcite.adapter.enumerable.RexImpTable;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RexImpTableInnerBuilderTest {
    @Test
    void suppliesIndependentAggregateImplementors() {
        AggImplementor first = RexImpTable.INSTANCE.get(SqlStdOperatorTable.SUM, false);
        AggImplementor second = RexImpTable.INSTANCE.get(SqlStdOperatorTable.SUM, false);

        assertThat(first).isNotNull().isNotSameAs(second);
        assertThat(second).isNotNull();
    }
}
