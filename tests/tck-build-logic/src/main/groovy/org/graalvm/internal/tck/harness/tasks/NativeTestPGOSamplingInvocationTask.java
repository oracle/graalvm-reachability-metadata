/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import java.util.List;

/**
 * Task that builds PGO-sampling native test images, with the analysis
 * call-tree CSV dump enabled, on matching subprojects.
 * <p>
 * Supports the phase-7 near-call analyzer of §WF-code-coverage-improvement.
 */
@SuppressWarnings("unused")
public abstract class NativeTestPGOSamplingInvocationTask extends AllCoordinatesExecTask {

    @Override
    public List<String> commandFor(String coordinates) {
        return List.of(
                tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("gradlew").toString(),
                "nativeTestPGOSampling"
        );
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "PGO-sampling native image compilation failed for " + coordinates
                + " with exit code " + exitCode + ".";
    }
}
