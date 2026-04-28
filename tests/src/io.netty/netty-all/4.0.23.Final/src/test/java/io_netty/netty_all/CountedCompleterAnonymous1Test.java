/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.internal.chmv8.CountedCompleter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CountedCompleterAnonymous1Test {
    @Test
    void countedCompleterInitializesUnsafeAccessAndCompletesTask() {
        SummingCompleter task = new SummingCompleter(new int[] { 1, 2, 3, 4 });

        task.addToPendingCount(2);
        Assertions.assertEquals(2, task.getPendingCount());
        Assertions.assertEquals(2, task.decrementPendingCountUnlessZero());
        Assertions.assertTrue(task.compareAndSetPendingCount(1, 0));

        task.compute();

        Assertions.assertEquals(Integer.valueOf(10), task.getRawResult());
        Assertions.assertEquals(0, task.getPendingCount());
        Assertions.assertTrue(task.isDone());
    }

    private static final class SummingCompleter extends CountedCompleter<Integer> {
        private final int[] values;
        private int result;

        private SummingCompleter(int[] values) {
            this.values = values;
        }

        @Override
        public void compute() {
            int sum = 0;
            for (int value : values) {
                sum += value;
            }
            result = sum;
            tryComplete();
        }

        @Override
        public Integer getRawResult() {
            return Integer.valueOf(result);
        }
    }
}
