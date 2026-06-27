/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

import static org.assertj.core.api.Assertions.assertThat;

public class ScheduledMethodRunnableTest {
    @Test
    void invokesNamedNoArgumentMethodOnTarget() throws Exception {
        ScheduledTarget target = new ScheduledTarget();

        ScheduledMethodRunnable runnable = new ScheduledMethodRunnable(target, "markScheduled");
        runnable.run();

        assertThat(target.getInvocationCount()).isEqualTo(1);
        assertThat(runnable.getTarget()).isSameAs(target);
        assertThat(runnable.getMethod().getName()).isEqualTo("markScheduled");
        assertThat(runnable.toString()).endsWith(".markScheduled");
    }

    public static final class ScheduledTarget {
        private int invocationCount;

        public void markScheduled() {
            this.invocationCount++;
        }

        int getInvocationCount() {
            return this.invocationCount;
        }
    }
}
