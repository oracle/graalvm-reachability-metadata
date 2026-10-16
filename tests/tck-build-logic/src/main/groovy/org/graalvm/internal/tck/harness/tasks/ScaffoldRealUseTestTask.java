/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.internal.tck.Coordinates;
import org.graalvm.internal.tck.ScaffoldFlavor;
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;
import org.graalvm.internal.tck.utils.CoordinateUtils;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Creates the real-use project of one supported coordinate from the real-use template.
 *
 * The project builds with the same convention plugin as a `tests/` project and declares only the
 * target library; its scenarios and supporting libraries come later
 * (§real-use-tests/AR-real-use-test-harness.1, §real-use-tests/AR-real-use-test-harness.2).
 * It takes the build file and test source directory of a `tests/` project in the language of the
 * coordinate's index entry (§real-use-tests/FS-real-use-test-contract.1.5).
 */
@SuppressWarnings("unused")
public abstract class ScaffoldRealUseTestTask extends SingleCoordinateTask {

    /// Project file -> template resource it is rendered from, beside the language's build file.
    private static final Map<String, String> FILES = Map.of(
            "settings.gradle", "/real-use-scaffold/settings.gradle.template",
            "gradle.properties", "/scaffold/gradle.properties.template",
            ".gitignore", "/scaffold/.gitignore.template"
    );

    @TaskAction
    public void scaffold() throws IOException {
        String coordinates = resolveSingleCoordinate();
        Coordinates parsed = CoordinateUtils.fromString(coordinates);
        Path repoRoot = tckExtension.getRepoRoot().get().getAsFile().toPath();
        Path project = repoRoot.resolve("real-use-tests").resolve("src")
                .resolve(parsed.group()).resolve(parsed.artifact()).resolve(parsed.version());
        if (Files.exists(project)) {
            throw new GradleException("The real-use project for " + coordinates + " already exists: " + project);
        }
        ScaffoldFlavor flavor = ScaffoldFlavor.fromLanguage(indexEntry(repoRoot, parsed).language());
        write(project.resolve("build.gradle"), flavor.buildTemplatePath(), parsed);
        for (Map.Entry<String, String> file : FILES.entrySet()) {
            write(project.resolve(file.getKey()), file.getValue(), parsed);
        }
        Files.createDirectories(project.resolve(flavor.testSourceDirectory()));
        getLogger().lifecycle("Scaffolded the {} real-use project for {} at {}", flavor, coordinates, project);
    }

    /// The index entry whose `tested-versions` lists the coordinate's version.
    private static MetadataVersionsIndexEntry indexEntry(Path repoRoot, Coordinates coordinates) throws IOException {
        Path index = repoRoot.resolve("metadata").resolve(coordinates.group()).resolve(coordinates.artifact()).resolve("index.json");
        if (!Files.isRegularFile(index)) {
            throw new GradleException("No metadata index for " + coordinates + ": " + index);
        }
        return MetadataCoordinateSupport.readIndexEntries(new ObjectMapper(), index).stream()
                .filter(entry -> entry.testedVersions() != null && entry.testedVersions().contains(coordinates.version()))
                .findFirst()
                .orElseThrow(() -> new GradleException("No entry of " + index + " lists " + coordinates.version() + " in tested-versions"));
    }

    private static void write(Path target, String resource, Coordinates coordinates) throws IOException {
        Files.createDirectories(target.getParent());
        Files.writeString(target, CoordinateUtils.replace(loadResource(resource), coordinates), StandardCharsets.UTF_8);
    }

    private static String loadResource(String name) throws IOException {
        try (InputStream stream = ScaffoldRealUseTestTask.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IOException("Resource '%s' not found".formatted(name));
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
