/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.GradleException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the selected real-use projects on the JVM.
 *
 * With `-Ptck.realUse.agentOutput=<directory>` the tests run under the native-image agent in the
 * project's conditional mode, filtered by its `user-code-filter.json`, and the collected metadata
 * is copied to that directory even when a test fails (§real-use-tests/AR-real-use-test-harness.2).
 */
@SuppressWarnings("unused")
public abstract class RealUseJavaTestInvocationTask extends RealUseExecTask {

    static final String AGENT_OUTPUT_PROPERTY = "tck.realUse.agentOutput";

    @Override
    public List<String> commandFor(String coordinates) {
        List<String> command = new ArrayList<>(List.of(gradlew()));
        Object agentOutput = getProject().findProperty(AGENT_OUTPUT_PROPERTY);
        if (agentOutput == null) {
            command.add("test");
            return command;
        }
        Path outputDirectory = Path.of(agentOutput.toString());
        if (!outputDirectory.isAbsolute()) {
            throw new GradleException("Property '" + AGENT_OUTPUT_PROPERTY + "' must be an absolute path. Got: " + agentOutput);
        }
        command.addAll(List.of(
                "--continue", "-Pagent", "test", "copyRealUseAgentMetadata",
                "-P" + AGENT_OUTPUT_PROPERTY + "=" + outputDirectory
        ));
        return command;
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "Real-use JVM tests failed for " + coordinates;
    }
}
