/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.graalvm.internal.tck.utils.CoordinateUtils;
import org.graalvm.internal.tck.utils.NativeImageConfigUtils;
import org.graalvm.internal.tck.utils.TestInfraLoggingUtils;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        return defaultArgs;
    }


    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "Test for " + coordinates + " failed with exit code " + exitCode + ".";
    }

    @Override
    protected void beforeEach(String coordinates, List<String> command) {
        String coordinateFilter = Objects.toString(getProject().findProperty("coordinates"), "");
        if (CoordinateUtils.isFractionalBatch(coordinateFilter)) {
            String nativeImageMode = NativeImageConfigUtils.resolveSelectedMode(
                    System.getenv("GVM_TCK_NATIVE_IMAGE_MODE"),
                    Objects.toString(getProject().findProperty("tck.nativeImageMode"), null)
            );
            String parallelismProperty = Objects.toString(getProject().findProperty("parallelism"), "");
            Integer parallelism = parallelismProperty.isBlank()
                    ? null
                    : TestInfraLoggingUtils.parseParallelism(parallelismProperty);
            for (String line : TestInfraLoggingUtils.batchReproducerLines("test", nativeImageMode, coordinates, parallelism)) {
                getLogger().lifecycle(line);
            }
        }
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
