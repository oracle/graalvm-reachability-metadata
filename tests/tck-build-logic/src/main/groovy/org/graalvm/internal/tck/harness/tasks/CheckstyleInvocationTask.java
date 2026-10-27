/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Task that is used to run checkstyle task on subprojects.
 *
 * A coordinate's real-use project is checked after its `tests/` project
 * (§real-use-tests/AR-real-use-test-harness.2).
 */
@SuppressWarnings("unused")
public abstract class CheckstyleInvocationTask extends AllCoordinatesExecTask {

    @Override
    public List<String> commandFor(String coordinates) {
        List<String> command = new ArrayList<>(List.of(
                tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("gradlew").toString(),
                "checkstyle"
        ));
        appendProperty(command, "includeCodeCoverageSuite");
        return command;
    }

    @Override
    protected List<Path> workingDirsFor(String coordinates) {
        List<Path> dirs = new ArrayList<>(super.workingDirsFor(coordinates));
        tckExtension.getRealUseTestDir(coordinates).ifPresent(dirs::add);
        return dirs;
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "Checkstyle failed";
    }
}
