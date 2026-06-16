/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import java.util.List;

/**
 * Task that builds instrumented-PGO native test images, with the analysis
 * call-tree dump enabled, on matching subprojects.
 * <p>
 * Supports the phase-6 PGO discovery analyzer of §WF-code-coverage-improvement.
 */
@SuppressWarnings("unused")
public abstract class NativeTestPGOInstrumentInvocationTask extends AllCoordinatesExecTask {

    @Override
    public List<String> commandFor(String coordinates) {
        return List.of(
                tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("gradlew").toString(),
                "nativeTestPGOInstrument"
        );
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "Instrumented PGO native image compilation failed for " + coordinates
                + " with exit code " + exitCode + ".";
    }
}
