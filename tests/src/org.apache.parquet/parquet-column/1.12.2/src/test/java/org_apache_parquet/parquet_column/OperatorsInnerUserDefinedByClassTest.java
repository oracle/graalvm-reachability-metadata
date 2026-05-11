/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_column;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.parquet.filter2.predicate.FilterApi;
import org.apache.parquet.filter2.predicate.Operators.IntColumn;
import org.apache.parquet.filter2.predicate.Operators.UserDefined;
import org.apache.parquet.filter2.predicate.Operators.UserDefinedByClass;
import org.apache.parquet.filter2.predicate.Statistics;
import org.apache.parquet.filter2.predicate.UserDefinedPredicate;
import org.junit.jupiter.api.Test;

public class OperatorsInnerUserDefinedByClassTest {
    @Test
    void userDefinedPredicateClassIsInstantiatedThroughFilterApi() {
        IntColumn column = FilterApi.intColumn("metrics.score");

        UserDefined<Integer, GreaterThanTenPredicate> predicate = FilterApi.userDefined(
                column,
                GreaterThanTenPredicate.class);

        assertTrue(predicate instanceof UserDefinedByClass<?, ?>);
        UserDefinedByClass<?, ?> byClass = (UserDefinedByClass<?, ?>) predicate;
        assertSame(column, byClass.getColumn());
        assertEquals(GreaterThanTenPredicate.class, byClass.getUserDefinedPredicateClass());
        assertEquals(
                "userdefinedbyclass(metrics.score, " + GreaterThanTenPredicate.class.getName() + ")",
                byClass.toString());

        GreaterThanTenPredicate firstInstance = predicate.getUserDefinedPredicate();
        GreaterThanTenPredicate secondInstance = predicate.getUserDefinedPredicate();

        assertNotSame(firstInstance, secondInstance);
        assertFalse(firstInstance.keep(10));
        assertTrue(firstInstance.keep(11));
        assertTrue(firstInstance.canDrop(new Statistics<>(1, 10, Integer::compareTo)));
        assertFalse(firstInstance.canDrop(new Statistics<>(1, 11, Integer::compareTo)));
        assertTrue(firstInstance.inverseCanDrop(new Statistics<>(11, 20, Integer::compareTo)));
        assertFalse(firstInstance.inverseCanDrop(new Statistics<>(10, 20, Integer::compareTo)));
    }

    public static class GreaterThanTenPredicate extends UserDefinedPredicate<Integer> {
        public GreaterThanTenPredicate() {
        }

        @Override
        public boolean keep(Integer value) {
            return value != null && value > 10;
        }

        @Override
        public boolean canDrop(Statistics<Integer> statistics) {
            return statistics.getMax() <= 10;
        }

        @Override
        public boolean inverseCanDrop(Statistics<Integer> statistics) {
            return statistics.getMin() > 10;
        }
    }
}
