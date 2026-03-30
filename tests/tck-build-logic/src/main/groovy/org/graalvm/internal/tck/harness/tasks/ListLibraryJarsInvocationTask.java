/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import java.util.List;

/**
 * Runs `listLibraryJars` for each matching coordinate and prints resolved tested-library JARs.
 */
@SuppressWarnings("unused")
public abstract class ListLibraryJarsInvocationTask extends AllCoordinatesExecTask {

    @Override
    public List<String> commandFor(String coordinates) {
        return List.of(
                tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("gradlew").toString(),
                "--quiet",
                "listLibraryJars"
        );
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "Listing library JARs failed for " + coordinates + " with exit code " + exitCode + ".";
    }
}
