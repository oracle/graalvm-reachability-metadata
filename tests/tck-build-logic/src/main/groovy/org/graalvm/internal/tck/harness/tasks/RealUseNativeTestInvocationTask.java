/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the native test image of each selected real-use project and runs it.
 *
 * The image gets the shipped metadata the way a consumer build does; with
 * `-Ptck.realUse.exactReachability=true` it is built with `--exact-reachability-metadata`
 * (§real-use-tests/AR-real-use-test-harness.2).
 */
@SuppressWarnings("unused")
public abstract class RealUseNativeTestInvocationTask extends RealUseExecTask {

    @Inject
    public abstract ProviderFactory getProviders();

    @Override
    public List<String> commandFor(String coordinates) {
        List<String> command = new ArrayList<>(List.of(gradlew(), "nativeTest"));
        var installPathsProperty = getProviders().environmentVariable("TCK_JDK_INSTALLATION_PATHS");
        if (installPathsProperty.isPresent()) {
            command.add("-Porg.gradle.java.installations.auto-detect=false");
            command.add("-Porg.gradle.java.installations.paths=" + installPathsProperty.get());
        }
        appendProperty(command, "tck.realUse.exactReachability");
        return command;
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "Real-use native tests failed for " + coordinates;
    }
}
