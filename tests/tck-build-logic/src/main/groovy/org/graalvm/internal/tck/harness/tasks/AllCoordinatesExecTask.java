/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecSpec;
import org.graalvm.internal.tck.utils.CoordinateUtils;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.graalvm.internal.tck.Utils.readIndexFile;
import static org.graalvm.internal.tck.Utils.splitCoordinates;

/**
 * Base task that resolves coordinates (via CoordinatesAwareTask) and executes a command for each coordinate.
 * Subclasses implement commandFor(String coordinates) and may override hooks for logging.
 */
@SuppressWarnings("unused")
public abstract class AllCoordinatesExecTask extends CoordinatesAwareTask {

    @Inject
    public abstract ExecOperations getExecOperations();

    /**
     * Subclasses must return the command line to run for the given coordinates.
     */
    public abstract List<String> commandFor(String coordinates);

    /**
     * Customize error message.
     */
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "Execution failed for " + coordinates + " with exit code " + exitCode;
    }

    /**
     * Hook invoked before executing each coordinate.
     */
    protected void beforeEach(String coordinates, List<String> command) {
        // no-op
    }

    /**
     * Hook invoked after executing each coordinate on success.
     */
    protected void afterEach(String coordinates) {
        // no-op
    }

    @TaskAction
    public final void runAll() {
        List<String> coords = resolveCoordinates();
        if (coords.isEmpty()) {
            // If the user explicitly provided -Pcoordinates (non-empty, non-batch), fail fast with a clear error.
            List<String> override = getCoordinatesOverride().getOrNull();
            boolean hasProgrammaticOverride = override != null && !override.isEmpty();

            String filter = Objects.toString(getProject().findProperty("coordinates"), "");
            boolean userProvidedFilter = filter != null && !filter.isBlank();
            boolean isBatch = userProvidedFilter && CoordinateUtils.isFractionalBatch(filter);

            if (!hasProgrammaticOverride && userProvidedFilter && !isBatch) {
                throw new GradleException("No matching coordinates found for '" + filter + "'. " +
                        "The specified coordinate or filter does not exist. " +
                        "Tip: run './gradlew listCoordinates -Pcoordinates=" + filter + "' to inspect matches.");
            } else {
                // Nothing matched (either no filter provided or programmatic override); do not fail.
                getLogger().lifecycle("No matching coordinates found. Nothing to do.");
                return;
            }
        }
        for (String c : coords) {
            runSingle(c);
        }
    }

    private void runSingle(String coordinates) {
        List<String> command = commandFor(coordinates);
        beforeEach(coordinates, command);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        var execResult = getExecOperations().exec((ExecSpec spec) -> {
            this.configureSpec(spec, coordinates, command);
            if (streamSubprocessOutput(coordinates)) {
                spec.setStandardOutput(new TeeOutputStream(out, System.out));
                spec.setErrorOutput(new TeeOutputStream(err, System.err));
            } else {
                spec.setStandardOutput(out);
                spec.setErrorOutput(err);
            }
        });

        // write output file like AbstractSubprojectTask
        String hash = md5(String.join(",", command));
        File outputFile = getProject().getLayout().getBuildDirectory().file("tests/" + coordinates + "/" + hash + ".out").get().getAsFile();
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        String content = "Standard out\n" +
                "-----\n" +
                out.toString(StandardCharsets.UTF_8) +
                "\n-----\n" +
                "Standard err\n" +
                "----\n" +
                err.toString(StandardCharsets.UTF_8) +
                "\n----\n";
        try {
            Files.writeString(outputFile.toPath(), content, StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new GradleException("Failed to write test output to " + outputFile, e);
        }

        int exitCode = execResult.getExitValue();
        if (exitCode != 0) {
            throw new GradleException(errorMessageFor(coordinates, exitCode));
        }
        afterEach(coordinates);
    }

    @SuppressWarnings("unchecked")
    protected void configureSpec(ExecSpec spec, String coordinates, List<String> command) {
        List<String> parts = splitCoordinates(coordinates);
        String groupId = parts.get(0);
        String artifactId = parts.get(1);
        String version = parts.get(2);
        Path metadataDir = tckExtension.getMetadataDir(coordinates);
        boolean override = false;

        var metadataIndex = readIndexFile(metadataDir.getParent());
        for (Object entryObj : (Iterable<?>) metadataIndex) {
            Map<String, Object> entry = (Map<String, Object>) entryObj;
            if (((List<String>) entry.get("tested-versions")).contains(version)) {
                if (entry.containsKey("override")) {
                    Object ov = entry.get("override");
                    if (ov instanceof Boolean b) {
                        override |= b;
                    } else if (ov != null) {
                        override |= Boolean.parseBoolean(ov.toString());
                    }
                }
                break;
            }
        }

        Path testDir = tckExtension.getTestDir(coordinates);

        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("GVM_TCK_LC", coordinates);
        env.put("GVM_TCK_EXCLUDE", Boolean.toString(override));
        if (System.getenv("GVM_TCK_LV") == null) {
            env.put("GVM_TCK_LV", version);
        }
        if (System.getenv("GVM_TCK_NATIVE_IMAGE_MODE") != null) {
            env.put("GVM_TCK_NATIVE_IMAGE_MODE", System.getenv("GVM_TCK_NATIVE_IMAGE_MODE"));
        }
        env.put("GVM_TCK_MD", metadataDir.toAbsolutePath().toString());
        env.put("GVM_TCK_TCKDIR", tckExtension.getTckRoot().get().getAsFile().toPath().toAbsolutePath().toString());

        spec.environment(env);
        spec.commandLine(command);
        spec.workingDir(testDir.toAbsolutePath().toFile());
        spec.setIgnoreExitValue(true);
        spec.setStandardOutput(System.out);
        spec.setErrorOutput(System.err);
    }

    protected boolean streamSubprocessOutput(String coordinates) {
        return true;
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                sb.append(Character.forDigit((b & 0xF0) >> 4, 16));
                sb.append(Character.forDigit(b & 0x0F, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // Fallback to hex of hashCode to avoid failing the build because of MD5 unavailability
            return Integer.toHexString(s.hashCode());
        }
    }
}
