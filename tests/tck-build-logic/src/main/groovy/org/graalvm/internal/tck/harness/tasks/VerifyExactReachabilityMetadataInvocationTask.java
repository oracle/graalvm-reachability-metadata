/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import java.util.ArrayList;
import java.util.List;

/**
 * Task that builds native test images with exact reachability metadata enabled.
 */
@SuppressWarnings("unused")
public abstract class VerifyExactReachabilityMetadataInvocationTask extends AllCoordinatesExecTask {

    @Override
    public List<String> commandFor(String coordinates) {
        List<String> command = new ArrayList<>(List.of(
                tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("gradlew").toString(),
                "verifyExactReachabilityMetadata"
        ));
        appendProperty(command, "metadataConfigDirs");
        appendProperty(command, "exactPackages");
        return command;
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "Exact reachability metadata verification failed for "
                + coordinates
                + " with exit code "
                + exitCode
                + ".";
    }

    private void appendProperty(List<String> command, String propertyName) {
        Object propertyValue = getProject().findProperty(propertyName);
        if (propertyValue != null) {
            command.add("-P" + propertyName + "=" + propertyValue);
        }
    }
}
