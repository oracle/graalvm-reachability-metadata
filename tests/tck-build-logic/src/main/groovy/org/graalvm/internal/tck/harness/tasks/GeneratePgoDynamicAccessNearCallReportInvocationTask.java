/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.GradleException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Runs the subproject PGO near-call diagnostic task for each matching coordinate.
 */
@SuppressWarnings("unused")
public abstract class GeneratePgoDynamicAccessNearCallReportInvocationTask extends AllCoordinatesExecTask {

    @Override
    public List<String> commandFor(String coordinates) {
        List<String> command = new ArrayList<>(List.of(
                tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("gradlew").toString(),
                "generatePgoDynamicAccessNearCallReport",
                "-Ptck.generatePgoDynamicAccessNearCallReport=true"
        ));
        appendProperty(command, "pgoSamplingPeriodMicros");
        return command;
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "PGO dynamic-access near-call report generation failed for " + coordinates + " with exit code " + exitCode + ".";
    }

    @Override
    protected boolean streamSubprocessOutput(String coordinates) {
        return true;
    }

    @Override
    protected void afterEach(String coordinates) {
        Path reportDir = tckExtension.getTestDir(coordinates)
                .resolve("build")
                .resolve("reports")
                .resolve("pgo-near-call");

        if (!Files.isDirectory(reportDir)) {
            System.out.println("No PGO near-call report artifacts were generated for "
                    + coordinates
                    + " because the output directory is missing: "
                    + reportDir);
            return;
        }

        try (Stream<Path> files = Files.walk(reportDir)) {
            files.filter(Files::isRegularFile)
                    .sorted()
                    .forEach(path -> System.out.println(path.toAbsolutePath()));
        } catch (IOException e) {
            throw new GradleException("Failed to list PGO near-call report artifacts for " + coordinates, e);
        }
    }

    private void appendProperty(List<String> command, String propertyName) {
        Object propertyValue = getProject().findProperty(propertyName);
        if (propertyValue != null) {
            command.add("-P" + propertyName + "=" + propertyValue);
        }
    }
}
