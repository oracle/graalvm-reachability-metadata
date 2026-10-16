/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.graalvm.internal.tck.Coordinates;
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
 */
@SuppressWarnings("unused")
public abstract class ScaffoldRealUseTestTask extends SingleCoordinateTask {

    /// Project file -> template resource it is rendered from.
    private static final Map<String, String> FILES = Map.of(
            "build.gradle", "/real-use-scaffold/build.gradle.template",
            "settings.gradle", "/real-use-scaffold/settings.gradle.template",
            "gradle.properties", "/scaffold/gradle.properties.template",
            ".gitignore", "/scaffold/.gitignore.template"
    );

    @TaskAction
    public void scaffold() throws IOException {
        String coordinates = resolveSingleCoordinate();
        Coordinates parsed = CoordinateUtils.fromString(coordinates);
        Path project = tckExtension.getRepoRoot().get().getAsFile().toPath()
                .resolve("real-use-tests").resolve("src")
                .resolve(parsed.group()).resolve(parsed.artifact()).resolve(parsed.version());
        if (Files.exists(project)) {
            throw new GradleException("The real-use project for " + coordinates + " already exists: " + project);
        }
        for (Map.Entry<String, String> file : FILES.entrySet()) {
            Path target = project.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, CoordinateUtils.replace(loadResource(file.getValue()), parsed), StandardCharsets.UTF_8);
        }
        Files.createDirectories(project.resolve("src/test/java"));
        getLogger().lifecycle("Scaffolded the real-use project for {} at {}", coordinates, project);
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
