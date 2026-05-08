/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_maven_resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.junit.jupiter.api.Test;

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

    private static Dependency dependency(String groupId, String artifactId, String extension, String classifier,
            String version, String scope) {
        return new Dependency(new DefaultArtifact(groupId, artifactId, classifier, extension, version), scope);
    }
}
