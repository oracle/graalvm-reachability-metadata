/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_common.wildfly_common_jdk9_supplement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.wildfly.common.cpu.ProcessorInfo.availableProcessors;
import static org.wildfly.common.os.Process.getProcessId;
import static org.wildfly.common.os.Process.getProcessName;

import org.junit.jupiter.api.Test;

public class Wildfly_common_jdk9_supplementTest {
    @Test
    void processorInfoReportsTheRuntimeProcessorCount() {
        int runtimeProcessors = Runtime.getRuntime().availableProcessors();
        int wildflyProcessors = availableProcessors();

        assertThat(wildflyProcessors).isEqualTo(runtimeProcessors);
        assertThat(wildflyProcessors).isPositive();
    }

    @Test
    void processIdMatchesTheCurrentJavaProcess() {
        long processId = getProcessId();

        assertThat(processId).isEqualTo(ProcessHandle.current().pid());
        assertThat(processId).isPositive();
        assertThat(getProcessId()).isEqualTo(processId);
    }

    @Test
    void processNameIsResolvedAndStable() {
        String processName = getProcessName();

        assertThat(processName).isNotBlank();
        assertThat(getProcessName()).isSameAs(processName);
    }
}
