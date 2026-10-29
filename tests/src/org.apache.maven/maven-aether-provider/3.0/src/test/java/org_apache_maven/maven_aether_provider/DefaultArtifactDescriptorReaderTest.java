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
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.Exclusion;
import org.sonatype.aether.resolution.ArtifactDescriptorRequest;
import org.sonatype.aether.resolution.ArtifactDescriptorResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class DefaultArtifactDescriptorReaderTest {
    @TempDir
    Path directory;

    @Test
    void buildsAnEffectiveDescriptorUsingParentPropertiesAndDependencyManagement() throws Exception {
        RepositoryFixture repository = new RepositoryFixture(directory);
        repository.installPom("parent", "1.0", """
                <packaging>pom</packaging>
                <properties><engine.version>1.7</engine.version></properties>
                <dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>test.provider</groupId><artifactId>engine</artifactId>
                      <version>${engine.version}</version><scope>runtime</scope>
                    </dependency>
                  </dependencies>
                </dependencyManagement>
                """);
        repository.installPom("application", "1.0", """
                <parent>
                  <groupId>test.provider</groupId><artifactId>parent</artifactId><version>1.0</version>
                  <relativePath/>
                </parent>
                <licenses><license><name>Example license</name><url>https://example.invalid/license</url></license></licenses>
                <dependencies>
                  <dependency>
                    <groupId>test.provider</groupId><artifactId>engine</artifactId>
                    <optional>true</optional>
                    <exclusions><exclusion><groupId>test.provider</groupId><artifactId>legacy</artifactId></exclusion></exclusions>
                  </dependency>
                  <dependency>
                    <groupId>test.provider</groupId><artifactId>fixtures</artifactId><version>1.1</version>
                    <type>test-jar</type><scope>test</scope>
                  </dependency>
                </dependencies>
                """);

        ArtifactDescriptorResult result = readDescriptor(repository, "application");

        assertTrue(result.getExceptions().isEmpty());
        assertEquals(2, result.getDependencies().size());
        Dependency engine = result.getDependencies().get(0);
        assertEquals("engine", engine.getArtifact().getArtifactId());
        assertEquals("1.7", engine.getArtifact().getVersion());
        assertEquals("runtime", engine.getScope());
        assertTrue(engine.isOptional());
        assertEquals(List.of(new Exclusion("test.provider", "legacy", "*", "*")), List.copyOf(engine.getExclusions()));
        Dependency fixtures = result.getDependencies().get(1);
        assertEquals("jar", fixtures.getArtifact().getExtension());
        assertEquals("tests", fixtures.getArtifact().getClassifier());
        assertEquals("test", fixtures.getScope());
        assertEquals(1, result.getManagedDependencies().size());
        assertEquals("1.7", result.getManagedDependencies().get(0).getArtifact().getVersion());
        assertEquals(1, result.getProperties().get("license.count"));
        assertEquals("Example license", result.getProperties().get("license.0.name"));
    }

    @Test
    void followsPomRelocationAndReturnsTheReplacementDependencies() throws Exception {
        RepositoryFixture repository = new RepositoryFixture(directory);
        repository.installPom("legacy", "1.0", """
                <distributionManagement><relocation><artifactId>replacement</artifactId></relocation></distributionManagement>
                """);
        repository.installPom("replacement", "1.0", """
                <dependencies><dependency>
                  <groupId>test.provider</groupId><artifactId>engine</artifactId><version>1.4</version>
                </dependency></dependencies>
                """);

        ArtifactDescriptorResult result = readDescriptor(repository, "legacy");

        assertTrue(result.getExceptions().isEmpty());
        assertEquals("replacement", result.getArtifact().getArtifactId());
        assertEquals("test.provider", result.getArtifact().getGroupId());
        assertEquals("1.0", result.getArtifact().getVersion());
        assertEquals("jar", result.getArtifact().getExtension());
        assertEquals(1, result.getRelocations().size());
        assertEquals("legacy", result.getRelocations().get(0).getArtifactId());
        assertEquals(1, result.getDependencies().size());
        assertEquals("engine", result.getDependencies().get(0).getArtifact().getArtifactId());
        assertFalse(result.getDependencies().get(0).isOptional());
    }

    private ArtifactDescriptorResult readDescriptor(RepositoryFixture repository, String name) throws Exception {
        ArtifactDescriptorRequest request = new ArtifactDescriptorRequest();
        request.setArtifact(repository.artifact(name, "jar", "1.0"));
        return repository.system().readArtifactDescriptor(repository.session(), request);
    }
}
