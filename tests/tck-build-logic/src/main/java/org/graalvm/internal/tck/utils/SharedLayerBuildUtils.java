/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.process.ExecOperations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Builds JDK-only bases with identical construction and validation inputs.
/// Common bases may be reused; coordinate-specific bases always rebuild. §FS-tests.3.3
public final class SharedLayerBuildUtils {
    private SharedLayerBuildUtils() {
    }

    public static void build(Project project, ExecOperations execOperations, File baseLayerFile,
                             String nativeImage, String mode, List<String> arguments, boolean reuse) {
        if (!baseLayerFile.getName().endsWith(".nil")) {
            throw new GradleException("Native Image base-layer file must end with .nil: " + baseLayerFile);
        }
        File directory = baseLayerFile.getParentFile();
        File manifest = new File(directory, "manifest.json");
        String version = project.getProviders().exec(spec -> spec.commandLine(nativeImage, "--version"))
                .getStandardOutput().getAsText().get().trim();
        Map<String, Object> expected = BaseLayerUtils.expectedManifest(baseLayerFile, version, mode, arguments);
        if (reuse && baseLayerFile.isFile() && manifest.isFile()
                && expected.equals(new JsonSlurper().parse(manifest))) {
            project.getLogger().lifecycle("Reusing Native Image base layer at {}", baseLayerFile);
            return;
        }

        File temporary = new File(directory.getParentFile(), directory.getName() + ".tmp");
        project.getLogger().lifecycle("Building {}shared JDK base at {}", reuse ? "" : "uncached ", baseLayerFile);
        project.delete(directory, temporary);
        try {
            Files.createDirectories(directory.toPath());
            Files.createDirectories(temporary.toPath());
            String stem = baseLayerFile.getName().substring(0, baseLayerFile.getName().length() - ".nil".length());
            List<String> command = new ArrayList<>(List.of(nativeImage));
            command.addAll(arguments);
            command.add("-J-Djava.io.tmpdir=" + temporary.getAbsolutePath());
            command.addAll(List.of("-o", new File(directory, "lib" + stem).getAbsolutePath(),
                    "-H:+UnlockExperimentalVMOptions", BaseLayerUtils.layerCreateArgument(baseLayerFile),
                    "-H:-UnlockExperimentalVMOptions"));
            try {
                execOperations.exec(spec -> {
                    spec.workingDir(directory);
                    spec.commandLine(command);
                });
            } finally {
                project.delete(temporary);
            }
            if (!baseLayerFile.isFile()) {
                throw new GradleException("Native Image base-layer build did not create " + baseLayerFile);
            }
            Files.writeString(manifest.toPath(), JsonOutput.prettyPrint(JsonOutput.toJson(expected)) + System.lineSeparator());
        } catch (IOException exception) {
            throw new GradleException("Cannot prepare Native Image base layer at " + baseLayerFile, exception);
        }
    }
}
