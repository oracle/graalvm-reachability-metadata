/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_threads.jboss_threads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.threads.JBossExecutors;
import org.junit.jupiter.api.Test;

public class JBossExecutorsTest {
    @Test
    void directExecutorRunsTasksAndRejectingExecutorRejectsTasks() {
        AtomicBoolean taskRan = new AtomicBoolean(false);

        Executor directExecutor = JBossExecutors.directExecutor();
        directExecutor.execute(() -> taskRan.set(true));

        assertThat(taskRan.get()).isTrue();
        assertThatThrownBy(() -> JBossExecutors.rejectingExecutor("test rejection").execute(() -> taskRan.set(false)))
                .isInstanceOf(RejectedExecutionException.class)
                .hasMessageContaining("test rejection");
    }
}
