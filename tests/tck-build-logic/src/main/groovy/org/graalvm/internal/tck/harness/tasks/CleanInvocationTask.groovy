package org.graalvm.internal.tck.harness.tasks

import org.gradle.api.tasks.Input

import javax.inject.Inject

abstract class CleanInvocationTask extends AbstractSubprojectTask {

    @Inject
    CleanInvocationTask(String coordinates) {
        super(coordinates)
    }

    @Override
    @Input
    List<String> getCommand() {
        return [tckExtension.repoRoot.get().asFile.toPath().resolve("gradlew").toString(), "clean"]
    }

    @Override
    protected String getErrorMessage(int exitCode) {
        "Clean task failed"
    }

}
