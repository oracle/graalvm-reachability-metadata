/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ScheduledMethodRunnableTest {
    @Test
    void invokesNamedTask() throws Exception {
        Counter counter = new Counter();
        ScheduledMethodRunnable task = new ScheduledMethodRunnable(counter, "increment");
        task.run();
        task.run();
        assertSame(counter, task.getTarget());
        assertEquals(2, counter.count);
    }

    public static class Counter {
        private int count;

        public void increment() {
            count++;
        }
    }
}
