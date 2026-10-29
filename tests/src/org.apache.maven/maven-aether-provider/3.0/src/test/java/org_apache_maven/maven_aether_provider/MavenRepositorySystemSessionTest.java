/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_aether_provider;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.collection.CollectResult;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class MavenRepositorySystemSessionTest {
    @TempDir
    Path directory;

    @Test
    void collectsRuntimeDependenciesUsingMavenScopeAndOptionalityRules() throws Exception {
        RepositoryFixture repository = new RepositoryFixture(directory);
        repository.installPom("engine", "1.0", "");
        repository.installPom("runtime-helper", "1.0", "");
        repository.installPom("application", "1.0", """
                <dependencies>
                  <dependency>
                    <groupId>test.provider</groupId><artifactId>engine</artifactId><version>1.0</version>
                  </dependency>
                  <dependency>
                    <groupId>test.provider</groupId><artifactId>runtime-helper</artifactId><version>1.0</version>
                    <scope>runtime</scope>
                  </dependency>
                  <dependency>
                    <groupId>test.provider</groupId><artifactId>test-helper</artifactId><version>1.0</version>
                    <scope>test</scope>
                  </dependency>
                  <dependency>
                    <groupId>test.provider</groupId><artifactId>provided-helper</artifactId><version>1.0</version>
                    <scope>provided</scope>
                  </dependency>
                  <dependency>
                    <groupId>test.provider</groupId><artifactId>optional-helper</artifactId><version>1.0</version>
                    <optional>true</optional>
                  </dependency>
                </dependencies>
                """);
        repository.installPom("consumer", "1.0", """
                <dependencies><dependency>
                  <groupId>test.provider</groupId><artifactId>application</artifactId><version>1.0</version>
                </dependency></dependencies>
                """);
        CollectRequest request = new CollectRequest();
        request.setRoot(new Dependency(repository.artifact("consumer", "jar", "1.0"), "compile"));

        CollectResult result = repository.system().collectDependencies(repository.session(), request);

        assertTrue(result.getExceptions().isEmpty());
        DependencyNode root = result.getRoot();
        assertEquals("consumer", root.getDependency().getArtifact().getArtifactId());
        assertEquals(1, root.getChildren().size());
        DependencyNode application = root.getChildren().get(0);
        assertEquals("application", application.getDependency().getArtifact().getArtifactId());
        assertEquals(List.of("engine", "runtime-helper"), application.getChildren().stream()
                .map(node -> node.getDependency().getArtifact().getArtifactId()).toList());
        assertEquals(List.of("compile", "runtime"), application.getChildren().stream()
                .map(node -> node.getDependency().getScope()).toList());
    }
}
