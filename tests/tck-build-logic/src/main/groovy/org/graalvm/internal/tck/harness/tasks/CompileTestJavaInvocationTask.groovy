/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks

/**
 * Task that is used to compile subprojects with javac.
 */
@SuppressWarnings("unused")
abstract class CompileTestJavaInvocationTask extends AllCoordinatesExecTask {

    @Override
    List<String> commandFor(String coordinates) {
        return [tckExtension.repoRoot.get().asFile.toPath().resolve("gradlew").toString(), "compileTestJava"]
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        "Compilation failed"
    }
}
