/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.graalvm.internal.tck.Utils.readIndexFile;
import static org.graalvm.internal.tck.Utils.splitCoordinates;

/**
 * Task that is used to start subproject tests for matching coordinates.
 * Coordinate resolution is unified and handled by the base class.
 */
@SuppressWarnings("unused")
public abstract class TestInvocationTask extends AllCoordinatesExecTask {

    @Inject
    protected abstract ProviderFactory getProviders();

    @Override
    protected List<String> commandFor(String coordinates) {
        String gradlew = tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("gradlew").toString();
        List<String> defaultArgs = new ArrayList<>(java.util.List.of(gradlew, "nativeTest"));

        Provider<String> installPathsProperty = getProviders().environmentVariable("TCK_JDK_INSTALLATION_PATHS");
        if (installPathsProperty.isPresent()) {
            defaultArgs.add("-Porg.gradle.java.installations.auto-detect=false");
            defaultArgs.add("-Porg.gradle.java.installations.paths=" + installPathsProperty.get());
        }

        try {
            Path testDir = tckExtension.getTestDir(coordinates);
            java.nio.file.Path idx = testDir.resolve("index.json");
            if (!java.nio.file.Files.exists(idx)) {
                return defaultArgs;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> testIndex = (Map<String, Object>) readIndexFile(testDir);
            if (!testIndex.containsKey("test-command")) {
                return defaultArgs;
            }

            Path metadataDir = tckExtension.getMetadataDir(coordinates);
            Object value = testIndex.get("test-command");
            if (value instanceof List<?> list) {
                List<String> cmds = new ArrayList<>(list.size());
                for (Object o : list) {
                    if (o != null) {
                        cmds.add(processCommand(String.valueOf(o), metadataDir, coordinates));
                    }
                }
                return cmds;
            } else {
                // Fallback: single string
                return java.util.List.of(processCommand(String.valueOf(value), metadataDir, coordinates));
            }
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
     * @param coordinates maven coordinates
     * @return final command
     */
    public static String processCommand(String cmd, Path metadataDir, String coordinates) {
        java.util.List<String> parts = splitCoordinates(coordinates);
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
