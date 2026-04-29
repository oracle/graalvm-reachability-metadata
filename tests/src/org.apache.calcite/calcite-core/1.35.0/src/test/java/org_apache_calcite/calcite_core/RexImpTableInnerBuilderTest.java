/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.calcite.adapter.enumerable.AggImplementor;
import org.apache.calcite.adapter.enumerable.RexImpTable;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.junit.jupiter.api.Test;

public class RexImpTableInnerBuilderTest {
    @Test
    void builtInAggregateImplementorsAreCreatedFromBuilderSuppliers() {
        AggImplementor countImplementor = RexImpTable.INSTANCE.get(SqlStdOperatorTable.COUNT, false);
        AggImplementor secondCountImplementor = RexImpTable.INSTANCE.get(SqlStdOperatorTable.COUNT, false);

        assertThat(countImplementor).isNotNull();
        assertThat(secondCountImplementor).isNotNull();
        assertThat(secondCountImplementor).isNotSameAs(countImplementor);
    }
}
