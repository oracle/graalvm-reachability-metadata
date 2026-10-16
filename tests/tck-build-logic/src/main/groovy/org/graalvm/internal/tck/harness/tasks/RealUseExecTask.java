/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.GradleException;

import java.nio.file.Path;
import java.util.List;

/**
 * Base for the `realUse*` tasks: runs a command in each selected real-use project.
 *
 * The selection is the `-Pcoordinates=` filter applied to the coordinates that have a real-use
 * project, so a coordinate without one is skipped and no `tests/` project is ever selected
 * (§real-use-tests/AR-real-use-test-harness.2, §real-use-tests/AR-real-use-test-harness.3).
 */
public abstract class RealUseExecTask extends AllCoordinatesExecTask {

    @Override
    protected List<String> computeMatchingCoordinates(String filter) {
        return tckExtension.getMatchingRealUseCoordinates(filter);
    }

    @Override
    protected List<Path> workingDirsFor(String coordinates) {
        return List.of(tckExtension.getRealUseTestDir(coordinates)
                .orElseThrow(() -> new GradleException("Missing real-use project for " + coordinates)));
    }

    @Override
    protected boolean failWhenFilterSelectsNothing() {
        return false;
    }

    protected String gradlew() {
        return tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("gradlew").toString();
    }
}
