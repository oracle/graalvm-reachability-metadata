/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_maven_resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.bootstrap.util.DependencyUtils;

public class Quarkus_bootstrap_maven_resolverTest {

    @Test
    void parsesMavenArtifactCoordinatesWithDefaultsAndClassifiers() {
        Artifact minimal = DependencyUtils.toArtifact("org.example:demo");
        assertEquals("org.example", minimal.getGroupId());
        assertEquals("demo", minimal.getArtifactId());
        assertEquals("jar", minimal.getExtension());
        assertEquals("", minimal.getClassifier());
        assertEquals("", minimal.getVersion());

        Artifact typed = DependencyUtils.toArtifact("org.example:demo:pom:1.0");
        assertEquals("pom", typed.getExtension());
        assertEquals("", typed.getClassifier());
        assertEquals("1.0", typed.getVersion());

        Artifact classified = DependencyUtils.toArtifact("org.example:demo:tests:jar:1.0");
        assertEquals("jar", classified.getExtension());
        assertEquals("tests", classified.getClassifier());
        assertEquals("1.0", classified.getVersion());
    }

    @Test
    void rejectsMalformedMavenArtifactCoordinates() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> DependencyUtils.toArtifact("org.example:"));

        assertEquals("Bad artifact coordinates org.example:, expected format is "
                + "<groupId>:<artifactId>[:<extension>|[:<classifier>:<extension>]]:<version>",
                exception.getMessage());
    }

    @Test
    void mergesDependenciesWithoutOverridingDominantEntriesAndExcludesScopes() {
        Dependency dominant = dependency("org.example", "core", "jar", "", "2.0", JavaScopes.COMPILE);
        Dependency recessive = dependency("org.example", "managed", "jar", "", null, JavaScopes.RUNTIME);
        Dependency excluded = dependency("org.example", "excluded", "jar", "", "1.0", JavaScopes.TEST);
        Dependency duplicateRecessive = dependency("org.example", "core", "jar", "", "1.0", JavaScopes.RUNTIME);

        List<Dependency> merged = DependencyUtils.mergeDependencies(
                List.of(dominant),
                List.of(duplicateRecessive, recessive, excluded),
                Map.of(),
                Set.of(JavaScopes.TEST));

        assertEquals(2, merged.size());
        assertEquals(dominant, merged.get(0));
        assertEquals("org.example:managed:jar:", merged.get(1).getArtifact().toString());
        assertEquals(JavaScopes.RUNTIME, merged.get(1).getScope());
    }

    @Test
    void readsMavenPomAndResolvesInheritedCiFriendlyCoordinates(@TempDir Path tempDir) throws Exception {
        String previousRevision = System.getProperty("revision");
        try {
            System.clearProperty("revision");
            Path pom = tempDir.resolve("pom.xml");
            Files.writeString(pom, """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <parent>
                            <groupId>org.example.parent</groupId>
                            <artifactId>parent-build</artifactId>
                            <version>parent-version</version>
                        </parent>
                        <artifactId>application</artifactId>
                        <version>${revision}</version>
                        <properties>
                            <revision>resolved-revision</revision>
                        </properties>
                    </project>
                    """);

            Model model = ModelUtils.readModel(pom);

            assertEquals(pom.toFile(), model.getPomFile());
            assertEquals("org.example.parent", ModelUtils.getGroupId(model));
            assertEquals("${revision}", ModelUtils.getRawVersion(model));
            assertEquals("resolved-revision", ModelUtils.getVersion(model));
        } finally {
            restoreProperty("revision", previousRevision);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static Dependency dependency(String groupId, String artifactId, String extension, String classifier,
            String version, String scope) {
        return new Dependency(new DefaultArtifact(groupId, artifactId, classifier, extension, version), scope);
    }
}
