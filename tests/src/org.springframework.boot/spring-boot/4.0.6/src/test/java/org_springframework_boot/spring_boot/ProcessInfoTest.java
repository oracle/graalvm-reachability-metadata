/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;

import org.springframework.boot.info.ProcessInfo;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessInfoTest {

    @Test
    void getVirtualThreadsReturnsSchedulerMetrics() throws InterruptedException {
        Thread virtualThread = Thread.startVirtualThread(Thread::yield);
        virtualThread.join();

        ProcessInfo.VirtualThreadsInfo virtualThreads = new ProcessInfo().getVirtualThreads();

        if (ClassUtils.isPresent("jdk.management.VirtualThreadSchedulerMXBean", null)) {
            assertThat(virtualThreads).isNotNull();
            assertThat(virtualThreads.getMounted()).isGreaterThanOrEqualTo(0);
            assertThat(virtualThreads.getQueued()).isGreaterThanOrEqualTo(0L);
            assertThat(virtualThreads.getParallelism()).isPositive();
            assertThat(virtualThreads.getPoolSize()).isGreaterThanOrEqualTo(0);
        }
        else {
            assertThat(virtualThreads).isNull();
        }
    }

}
