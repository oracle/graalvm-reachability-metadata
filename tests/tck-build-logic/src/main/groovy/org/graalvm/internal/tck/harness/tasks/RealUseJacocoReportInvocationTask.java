/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import java.util.List;

/**
 * Runs the selected real-use projects on the JVM and writes the JaCoCo XML report of the target
 * library's classes to the project's `build/reports/jacoco/test/jacocoTestReport.xml`
 * (§real-use-tests/AR-real-use-test-harness.2).
 */
@SuppressWarnings("unused")
public abstract class RealUseJacocoReportInvocationTask extends RealUseExecTask {

    @Override
    public List<String> commandFor(String coordinates) {
        return List.of(gradlew(), "jacocoTestReport");
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "Real-use JaCoCo report failed for " + coordinates;
    }
}
