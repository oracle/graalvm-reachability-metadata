/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_platform.junit_platform_engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.support.hierarchical.Node.ExecutionMode.CONCURRENT;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.support.hierarchical.ExclusiveResource;
import org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService;
import org.junit.platform.engine.support.hierarchical.HierarchicalTestExecutorService.TestTask;
import org.junit.platform.engine.support.hierarchical.Node.ExecutionMode;
import org.junit.platform.engine.support.hierarchical.ParallelExecutionConfiguration;
import org.junit.platform.engine.support.hierarchical.ResourceLock;

public class ForkJoinPoolHierarchicalTestExecutorServiceTest {

    @Test
    void createsForkJoinPoolAndExecutesSubmittedTask() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);

        try (ForkJoinPoolHierarchicalTestExecutorService executorService =
                new ForkJoinPoolHierarchicalTestExecutorService(new FixedParallelExecutionConfiguration())) {
            Future<Void> future = executorService.submit(new RecordingTestTask(executed));

            assertThat(future.get(5, TimeUnit.SECONDS)).isNull();
        }

        assertThat(executed).isTrue();
    }

    private static final class FixedParallelExecutionConfiguration implements ParallelExecutionConfiguration {

        @Override
        public int getParallelism() {
            return 2;
        }

        @Override
        public int getMinimumRunnable() {
            return 1;
        }

        @Override
        public int getMaxPoolSize() {
            return 4;
        }

        @Override
        public int getCorePoolSize() {
            return 2;
        }

        @Override
        public int getKeepAliveSeconds() {
            return 30;
        }
    }

    private static final class RecordingTestTask implements TestTask {

        private static final ResourceLock NO_OP_RESOURCE_LOCK = new NoOpResourceLock();

        private final AtomicBoolean executed;

        private RecordingTestTask(AtomicBoolean executed) {
            this.executed = executed;
        }

        @Override
        public ExecutionMode getExecutionMode() {
            return CONCURRENT;
        }

        @Override
        public ResourceLock getResourceLock() {
            return NO_OP_RESOURCE_LOCK;
        }

        @Override
        public void execute() {
            executed.set(true);
        }
    }

    private static final class NoOpResourceLock implements ResourceLock {

        @Override
        public ResourceLock acquire() {
            return this;
        }

        @Override
        public void release() {
        }

        @Override
        public List<ExclusiveResource> getResources() {
            return List.of();
        }

        @Override
        public boolean isExclusive() {
            return false;
        }
    }
}
