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

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class Wildfly_common_jdk9_supplementTest {
    private static final String JBOSS_PROCESS_NAME_PROPERTY = "jboss.process.name";

    @Test
    @Order(1)
    void processNameUsesConfiguredJbossProcessName() {
        String configuredProcessName = "wildfly-common-jdk9-supplement-test";
        String originalProcessName = System.getProperty(JBOSS_PROCESS_NAME_PROPERTY);
        System.setProperty(JBOSS_PROCESS_NAME_PROPERTY, configuredProcessName);
        try {
            assertThat(getProcessName()).isEqualTo(configuredProcessName);
        } finally {
            if (originalProcessName == null) {
                System.clearProperty(JBOSS_PROCESS_NAME_PROPERTY);
            } else {
                System.setProperty(JBOSS_PROCESS_NAME_PROPERTY, originalProcessName);
            }
        }
    }

    @Test
    @Order(2)
    void processorInfoReportsTheRuntimeProcessorCount() {
        int runtimeProcessors = Runtime.getRuntime().availableProcessors();
        int wildflyProcessors = availableProcessors();

        assertThat(wildflyProcessors).isEqualTo(runtimeProcessors);
        assertThat(wildflyProcessors).isPositive();
    }

    @Test
    @Order(3)
    void processIdMatchesTheCurrentJavaProcess() {
        long processId = getProcessId();

        assertThat(processId).isEqualTo(ProcessHandle.current().pid());
        assertThat(processId).isPositive();
        assertThat(getProcessId()).isEqualTo(processId);
    }

    @Test
    @Order(4)
    void processNameIsResolvedAndStable() {
        String processName = getProcessName();

        assertThat(processName).isNotBlank();
        assertThat(getProcessName()).isSameAs(processName);
    }
}
