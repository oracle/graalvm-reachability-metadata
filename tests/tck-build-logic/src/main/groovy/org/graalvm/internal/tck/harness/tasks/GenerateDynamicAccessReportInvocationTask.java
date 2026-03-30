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
import java.util.List;
import java.util.stream.Stream;

/**
 * Runs `nativeTestCompile` for each matching coordinate with dynamic-access tracking enabled and
 * prints generated JSON files under `build/native/nativeTestCompile/dynamic-access`.
 */
@SuppressWarnings("unused")
public abstract class GenerateDynamicAccessReportInvocationTask extends AllCoordinatesExecTask {

    @Override
    public List<String> commandFor(String coordinates) {
        return List.of(
                tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("gradlew").toString(),
                "nativeTestCompile",
                "-Ptck.generateDynamicAccessReport=true"
        );
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "Dynamic access listing failed for " + coordinates + " with exit code " + exitCode + ".";
    }

    @Override
    protected boolean streamSubprocessOutput(String coordinates) {
        return true;
    }

    @Override
    protected void afterEach(String coordinates) {
        Path dynamicAccessDir = tckExtension.getTestDir(coordinates)
                .resolve("build")
                .resolve("native")
                .resolve("nativeTestCompile")
                .resolve("dynamic-access");

        if (!Files.isDirectory(dynamicAccessDir)) {
            throw new GradleException("Dynamic access output directory was not created for " + coordinates + ": " + dynamicAccessDir);
        }

        try (Stream<Path> files = Files.walk(dynamicAccessDir)) {
            List<Path> jsonFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
            if (jsonFiles.isEmpty()) {
                System.out.println("No dynamic access JSON files were generated for " + coordinates + " in " + dynamicAccessDir);
                return;
            }
            jsonFiles.forEach(path -> System.out.println(path.toAbsolutePath()));
        } catch (IOException e) {
            throw new GradleException("Failed to list dynamic access JSON files for " + coordinates, e);
        }
    }
}
