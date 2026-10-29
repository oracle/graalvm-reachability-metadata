/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_aether_provider;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class DefaultServiceLocatorTest {
    @TempDir
    Path directory;

    @Test
    void installsAndResolvesAnArtifactThroughTheConfiguredRepositorySystem() throws Exception {
        RepositoryFixture repository = new RepositoryFixture(directory);
        Artifact installed = repository.install("greeting", "txt", "1.0", "hello from a local artifact");
        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(repository.artifact("greeting", "txt", "1.0"));

        ArtifactResult result = repository.system().resolveArtifact(repository.session(), request);

        assertTrue(result.isResolved());
        assertFalse(result.isMissing());
        assertEquals("hello from a local artifact", Files.readString(result.getArtifact().getFile().toPath()));
        assertEquals(repository.session().getLocalRepository(), result.getRepository());
        assertFalse(installed.getFile().equals(result.getArtifact().getFile()));
    }
}
