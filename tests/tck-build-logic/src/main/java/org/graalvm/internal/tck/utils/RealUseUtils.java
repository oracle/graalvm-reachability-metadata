/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedArtifact;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/// The work behind the tasks a real-use project adds to the convention plugin
/// (§real-use-tests/AR-real-use-test-harness.2).
public final class RealUseUtils {

    private static final Set<String> SCALA_TOOLCHAIN = Set.of(
            "scala-library", "scala-compiler", "scala-reflect", "scala3-library_3", "scala3-compiler_3");
    private static final String AGENT_METADATA = GeneralUtils.AGENT_OUTPUT_DIRECTORY + "/reachability-metadata.json";

    private RealUseUtils() {
    }

    /// The JARs of the libraries `build.gradle` declares; the harness's own JUnit, AssertJ,
    /// and test-support libraries, and the Kotlin or Scala runtime and compiler a language's
    /// project declares, are not declarations (§real-use-tests/FS-real-use-tests.1).
    public static List<File> declaredLibraryJars(Configuration declarations, Configuration runtimeClasspath) {
        Set<String> declared = declarations.getAllDependencies().stream()
                .filter(dependency -> dependency.getGroup() != null)
                .filter(dependency -> !isHarnessLibrary(dependency))
                .map(dependency -> dependency.getGroup() + ":" + dependency.getName())
                .collect(Collectors.toSet());
        List<File> jars = new ArrayList<>();
        for (ResolvedArtifact artifact : runtimeClasspath.getResolvedConfiguration().getResolvedArtifacts()) {
            String module = artifact.getModuleVersion().getId().getGroup() + ":" + artifact.getModuleVersion().getId().getName();
            if (artifact.getFile().getName().endsWith(".jar") && declared.contains(module)) {
                jars.add(artifact.getFile());
            }
        }
        return jars;
    }

    /// Writes `user-code-filter.json` with one `<package>.**` rule per package of the JARs that is
    /// not nested in another, in the repository's JSON format, and returns the rule count.
    public static int writeUserCodeFilter(File filterFile, List<File> jars) throws IOException {
        List<String> packages = JarUtils.coveringPackages(JarUtils.loadClassNames(
                jars.stream().distinct().map(File::toPath).toList()));
        if (packages.isEmpty()) {
            throw new GradleException("The declared libraries contain no packages: " + jars);
        }
        List<String> rules = new ArrayList<>();
        rules.add("    {\n      \"excludeClasses\": \"**\"\n    }");
        packages.forEach(pkg -> rules.add("    {\n      \"includeClasses\": \"" + pkg + ".**\"\n    }"));
        String json = "{\n  \"rules\": [\n" + String.join(",\n", rules) + "\n  ]\n}\n";
        Files.writeString(filterFile.toPath(), json, StandardCharsets.UTF_8);
        return packages.size();
    }

    /// Copies the native-image agent's merged test metadata into `outputDirectory`.
    public static void copyAgentMetadata(File buildDirectory, String outputDirectory) throws IOException {
        Path source = buildDirectory.toPath().resolve(AGENT_METADATA);
        if (!Files.isRegularFile(source)) {
            throw new GradleException("The native-image agent wrote no metadata at " + source);
        }
        Path target = Path.of(outputDirectory).resolve(source.getFileName());
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    static boolean isHarnessLibrary(Dependency dependency) {
        return dependency.getGroup().startsWith("org.junit")
                || dependency.getGroup().equals("org.assertj")
                || (dependency.getGroup().equals("org.graalvm") && dependency.getName().equals("tck-test-support"))
                || isLanguageToolchain(dependency);
    }

    /// The Kotlin standard library and test support the Kotlin plugin adds, and the Scala library and
    /// compiler a Scala project declares.
    private static boolean isLanguageToolchain(Dependency dependency) {
        String name = dependency.getName();
        return switch (dependency.getGroup()) {
            case "org.jetbrains.kotlin" -> name.startsWith("kotlin-stdlib") || name.startsWith("kotlin-test");
            case "org.scala-lang" -> SCALA_TOOLCHAIN.contains(name);
            default -> false;
        };
    }
}
