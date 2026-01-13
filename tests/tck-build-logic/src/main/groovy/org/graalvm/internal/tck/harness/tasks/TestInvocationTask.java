/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.graalvm.internal.tck.Utils.readIndexFile;
import static org.graalvm.internal.tck.Utils.splitCoordinates;

/**
 * Task that is used to start subproject tests for matching coordinates.
 * Coordinate resolution is unified and handled by the base class.
 */
@SuppressWarnings("unused")
public abstract class TestInvocationTask extends AllCoordinatesExecTask {

    @Inject
    public abstract ProviderFactory getProviders();

    @Override
    public List<String> commandFor(String coordinates) {
        List<String> defaultArgs = new ArrayList<>(List.of(
                tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("gradlew").toString(),
                "nativeTest"
        ));
        var installPathsProperty = getProviders().environmentVariable("TCK_JDK_INSTALLATION_PATHS");
        if (installPathsProperty.isPresent()) {
            defaultArgs.add("-Porg.gradle.java.installations.auto-detect=false");
            defaultArgs.add("-Porg.gradle.java.installations.paths=" + installPathsProperty.get());
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, List<String>> testIndex =
                    (Map<String, List<String>>) readIndexFile(tckExtension.getTestDir(coordinates));
            if (!testIndex.containsKey("test-command")) {
                return defaultArgs;
            }

            Path metadataDir = tckExtension.getMetadataDir(coordinates);
            return testIndex.get("test-command").stream()
                    .map(c -> processCommand(c, metadataDir, coordinates))
                    .collect(Collectors.toList());
        } catch (Exception ignored) {
            return defaultArgs;
        }
    }

    /**
     * Fills in template parameters in the command invocation.
     * Parameters are defined as <param_name> in cmd.
     *
     * @param cmd command line with parameters
     * @param metadataDir metadata directory location
     * @param coordinates coordinates in form group:artifact:version
     * @return final command
     */
    public static String processCommand(String cmd, Path metadataDir, String coordinates) {
        List<String> parts = splitCoordinates(coordinates);
        String groupId = parts.get(0);
        String artifactId = parts.get(1);
        String version = parts.get(2);
        return cmd.replace("<metadata_dir>", metadataDir.toAbsolutePath().toString())
                .replace("<group_id>", groupId)
                .replace("<artifact_id>", artifactId)
                .replace("<version>", version);
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "Test for " + coordinates + " failed with exit code " + exitCode + ".";
    }

    @Override
    protected void beforeEach(String coordinates, List<String> command) {
        getLogger().lifecycle("====================");
        getLogger().lifecycle("Testing library: {}", coordinates);
        getLogger().lifecycle("Command: `{}`", String.join(" ", command));
        getLogger().lifecycle("Executing test...");
        getLogger().lifecycle("-------");
    }

    @Override
    protected void afterEach(String coordinates) {
        getLogger().lifecycle("-------");
        getLogger().lifecycle("Test for {} passed.", coordinates);
        getLogger().lifecycle("====================");
    }
}
