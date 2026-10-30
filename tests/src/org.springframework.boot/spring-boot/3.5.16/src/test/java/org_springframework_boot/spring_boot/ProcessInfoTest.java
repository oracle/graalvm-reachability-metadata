/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.info.ProcessInfo;
import org.springframework.boot.info.ProcessInfo.VirtualThreadsInfo;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessInfoTest {

    @Test
    void reportsVirtualThreadSchedulerAndProcessIdentity() {
        ProcessInfo process = new ProcessInfo();
        VirtualThreadsInfo threads = process.getVirtualThreads();

        assertThat(threads).isNotNull();
        assertThat(threads.getParallelism()).isPositive();
        assertThat(threads.getMounted()).isGreaterThanOrEqualTo(0);
        assertThat(threads.getQueued()).isGreaterThanOrEqualTo(0);
        assertThat(threads.getPoolSize()).isGreaterThanOrEqualTo(0);
        assertThat(process.getPid()).isEqualTo(ProcessHandle.current().pid());
        assertThat(process.getCpus()).isPositive();
    }
}
