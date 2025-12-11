/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import java.util.List;

/**
 * Task that is used to compile native tests (Gradle 'nativeTestCompile') on subprojects.
 */
@SuppressWarnings("unused")
public abstract class NativeTestCompileInvocationTask extends AllCoordinatesExecTask {

    @Override
    public List<String> commandFor(String coordinates) {
        return List.of(
                tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("gradlew").toString(),
                "nativeTestCompile"
        );
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "Native test compilation failed";
    }
}
