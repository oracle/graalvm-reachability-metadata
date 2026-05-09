/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_rcm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.rcm.ResourceAllocationFilter;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.junit.jupiter.api.Test;

public class ResourceAllocationFilterTest {
    static {
        System.setProperty("org.glassfish.grizzly.rcm.policyMetric", "/api|0.50,/admin|0.25,/read|0.25");
    }

    @Test
    void constructorsExposeConfiguredStandardThreadPoolSize() {
        ResourceAllocationFilter defaultFilter = new ResourceAllocationFilter();
        ResourceAllocationFilter customFilter = new ResourceAllocationFilter(8);

        assertThat(defaultFilter.getStandardThreadPoolSize()).isEqualTo(5);
        assertThat(customFilter.getStandardThreadPoolSize()).isEqualTo(8);
    }

    @Test
    void filterRequestCreatesExecutorUsingConfiguredTokenRatios() throws Exception {
        ResourceAllocationFilter filter = new ResourceAllocationFilter(8);
        List<ExecutorService> executors = new ArrayList<>();

        try {
            ExecutorService apiExecutor = filter.filterRequest("/api");
            ExecutorService adminExecutor = filter.filterRequest("/admin");
            ExecutorService defaultExecutor = filter.filterRequest("/unconfigured");
            ExecutorService reusedDefaultExecutor = filter.filterRequest("/another-unconfigured");
            executors.add(apiExecutor);
            executors.add(adminExecutor);
            executors.add(defaultExecutor);

            assertExecutorConfiguration(apiExecutor, "RCM_5", 1, 5);
            assertExecutorConfiguration(adminExecutor, "RCM_3", 1, 3);
            assertExecutorConfiguration(defaultExecutor, "RCM_5", 1, 5);
            assertThat(reusedDefaultExecutor).isSameAs(defaultExecutor);
        } finally {
            for (ExecutorService executor : executors) {
                shutdownExecutor(executor);
            }
        }
    }

    @Test
    void filterRequestAllocatesFullStandardPoolWhenTokenRatioIsOne() {
        RecordingResourceAllocationFilter filter = new RecordingResourceAllocationFilter(8);
        String token = "/full-capacity";
        filter.addPrivilegedToken(token, 1.0);

        try {
            ExecutorService executor = filter.filterRequest(token);

            assertThat(filter.createdPoolSizes).containsExactly(8);
            assertThat(filter.createdExecutors).hasSize(1);
            assertThat(executor).isSameAs(filter.createdExecutors.get(0));
        } finally {
            filter.removePrivilegedToken(token);
            for (RecordingExecutorService executor : filter.createdExecutors) {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void handleReadStopsWhenHttpRequestLineIsIncomplete() throws IOException {
        ResourceAllocationFilter filter = new ResourceAllocationFilter();
        FilterChainContext context = new FilterChainContext();
        Buffer buffer = Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, "GET /api/without-version");
        int position = buffer.position();
        int limit = buffer.limit();
        context.setMessage(buffer);

        NextAction nextAction = filter.handleRead(context);

        assertThat(nextAction).isNotNull();
        assertThat(buffer.position()).isEqualTo(position);
        assertThat(buffer.limit()).isEqualTo(limit);
    }

    @Test
    void handleReadParsesContextRootAndSchedulesContinuationOnDedicatedExecutor() throws IOException {
        RecordingResourceAllocationFilter filter = new RecordingResourceAllocationFilter(8);
        FilterChainContext context = new FilterChainContext();
        Buffer buffer = Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER,
                "GET /read/?customer=42 HTTP/1.1\r\nHost: localhost\r\n\r\n");
        context.setMessage(buffer);

        NextAction nextAction = filter.handleRead(context);

        assertThat(nextAction).isNotNull();
        assertThat(filter.createdPoolSizes).containsExactly(3);
        assertThat(filter.createdExecutors).hasSize(1);
        assertThat(filter.createdExecutors.get(0).scheduledCommands).hasSize(1);
    }

    private static void shutdownExecutor(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        if (!(executor instanceof GrizzlyExecutorService)) {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void assertExecutorConfiguration(ExecutorService executor, String poolName, int corePoolSize,
            int maxPoolSize) {
        assertThat(executor).isInstanceOf(GrizzlyExecutorService.class);
        ThreadPoolConfig configuration = ((GrizzlyExecutorService) executor).getConfiguration();
        assertThat(configuration.getPoolName()).isEqualTo(poolName);
        assertThat(configuration.getCorePoolSize()).isEqualTo(corePoolSize);
        assertThat(configuration.getMaxPoolSize()).isEqualTo(maxPoolSize);
    }

    private static final class RecordingResourceAllocationFilter extends ResourceAllocationFilter {
        private final List<Integer> createdPoolSizes = new ArrayList<>();
        private final List<RecordingExecutorService> createdExecutors = new ArrayList<>();

        private RecordingResourceAllocationFilter(int standardThreadPoolSize) {
            super(standardThreadPoolSize);
        }

        @Override
        protected ExecutorService newThreadPool(int poolSize) {
            RecordingExecutorService executor = new RecordingExecutorService();
            createdPoolSizes.add(poolSize);
            createdExecutors.add(executor);
            return executor;
        }

        private void addPrivilegedToken(String token, double ratio) {
            privilegedTokens.put(token, ratio);
        }

        private void removePrivilegedToken(String token) {
            privilegedTokens.remove(token);
        }
    }

    private static final class RecordingExecutorService extends AbstractExecutorService {
        private final List<Runnable> scheduledCommands = new ArrayList<>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> commands = new ArrayList<>(scheduledCommands);
            scheduledCommands.clear();
            return commands;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            scheduledCommands.add(command);
        }
    }
}
