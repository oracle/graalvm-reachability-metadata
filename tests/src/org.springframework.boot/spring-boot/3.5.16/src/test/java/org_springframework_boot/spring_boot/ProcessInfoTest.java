/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.info.ProcessInfo;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessInfoTest {

    // Native Image has no VirtualThreadSchedulerMXBean, so getVirtualThreads() is not covered:
    // https://github.com/oracle/graal/issues/12802 (GR-72683).
    @Test
    void reportsProcessIdentity() {
        ProcessInfo process = new ProcessInfo();

        assertThat(process.getPid()).isEqualTo(ProcessHandle.current().pid());
        assertThat(process.getCpus()).isPositive();
    }
}
