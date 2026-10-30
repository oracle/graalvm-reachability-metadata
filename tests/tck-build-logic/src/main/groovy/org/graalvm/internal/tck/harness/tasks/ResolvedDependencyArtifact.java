/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// One JAR on the test project's resolved runtime classpath, a candidate owner of a foreign
/// condition (§FS-metadata.2.2).
public record ResolvedDependencyArtifact(String coordinate, Path file) {

    private static final Comparator<ResolvedDependency> BY_COORDINATE =
            Comparator.comparing(ResolvedDependencyArtifact::coordinateOf);

    /// Lists the JARs of the candidate owners on `classpath` (§FS-metadata.2.2): each first-level
    /// dependency, whether the tested library or one the build file declares beside it, and its
    /// closure. A first-level dependency on one of the `group:artifact` `harnessModules` the
    /// harness adds is no root, even when the build file repeats it, unless it is the
    /// `testedModule`; it is still listed where another root's closure reaches it. The walk is
    /// breadth-first in coordinate order and visits each coordinate once. The test project's own
    /// classes are not modules, so they never appear.
    public static List<ResolvedDependencyArtifact> runtimeClasspathJars(
            ResolvedConfiguration classpath,
            String testedModule,
            Set<String> harnessModules
    ) {
        Deque<ResolvedDependency> pending = new ArrayDeque<>(classpath.getFirstLevelModuleDependencies().stream()
                .filter((ResolvedDependency root) -> {
                    String module = root.getModuleGroup() + ":" + root.getModuleName();
                    return module.equals(testedModule) || !harnessModules.contains(module);
                })
                .sorted(BY_COORDINATE)
                .toList());
        Set<String> visited = new HashSet<>();
        List<ResolvedDependencyArtifact> jars = new ArrayList<>();
        while (!pending.isEmpty()) {
            ResolvedDependency dependency = pending.removeFirst();
            String coordinate = coordinateOf(dependency);
            if (!visited.add(coordinate)) {
                continue;
            }
            dependency.getModuleArtifacts().stream()
                    .map(ResolvedArtifact::getFile)
                    .filter((File file) -> file.isFile() && file.getName().endsWith(".jar"))
                    .map(File::toPath)
                    .sorted()
                    .forEach((Path file) -> jars.add(new ResolvedDependencyArtifact(coordinate, file)));
            dependency.getChildren().stream().sorted(BY_COORDINATE).forEach(pending::addLast);
        }
        return jars;
    }

    private static String coordinateOf(ResolvedDependency dependency) {
        return dependency.getModuleGroup() + ":" + dependency.getModuleName() + ":" + dependency.getModuleVersion();
    }
}
