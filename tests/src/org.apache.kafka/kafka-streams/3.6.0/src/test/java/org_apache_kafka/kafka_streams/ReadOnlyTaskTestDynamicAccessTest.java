/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import static org.assertj.core.api.Assertions.assertThat;

public class ReadOnlyTaskTestDynamicAccessTest {

    private static final String READ_ONLY_TASK_TEST_CLASS =
            "org.apache.kafka.streams.processor.internals.ReadOnlyTaskTest";
    private static final String FORBIDDEN_METHODS_TEST = "shouldThrowUnsupportedOperationExceptionForForbiddenMethods";

    @Test
    void shouldExerciseReadOnlyTaskReflectionCoverage() {
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectMethod(READ_ONLY_TASK_TEST_CLASS, FORBIDDEN_METHODS_TEST))
                .build();
        Launcher launcher = LauncherFactory.create();

        launcher.execute(request, listener);

        TestExecutionSummary summary = listener.getSummary();
        assertThat(summary.getTestsFoundCount()).isEqualTo(1);
        assertThat(summary.getTestsStartedCount()).isEqualTo(1);
        assertThat(summary.getTestsSkippedCount()).isZero();
    }
}
