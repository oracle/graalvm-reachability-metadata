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
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.VersionRequest;
import org.sonatype.aether.resolution.VersionResult;
import org.sonatype.aether.util.DefaultRepositoryCache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class DefaultVersionResolverTest {
    @TempDir
    Path directory;

    @Test
    void resolvesReleaseAndLatestFromAnInstalledReleaseAndReusesTheSessionCache() throws Exception {
        RepositoryFixture repository = new RepositoryFixture(directory);
        repository.install("catalog", "txt", "1.5", "current release");
        repository.session().setCache(new DefaultRepositoryCache());

        for (String requestedVersion : new String[]{"RELEASE", "LATEST"}) {
            VersionRequest request = new VersionRequest();
            request.setArtifact(repository.artifact("catalog", "txt", requestedVersion));
            request.setRequestContext("test");
            for (int attempt = 0; attempt < 2; attempt++) {
                VersionResult result = repository.system().resolveVersion(repository.session(), request);
                assertEquals("1.5", result.getVersion());
                assertEquals(repository.session().getLocalRepository(), result.getRepository());
                assertTrue(result.getExceptions().isEmpty());
            }
        }
    }

    @Test
    void resolvesAndReadsAnInstalledLocalSnapshot() throws Exception {
        RepositoryFixture repository = new RepositoryFixture(directory);
        repository.install("snapshot", "txt", "2.0-SNAPSHOT", "local development build");
        VersionRequest request = new VersionRequest();
        request.setArtifact(repository.artifact("snapshot", "txt", "2.0-SNAPSHOT"));

        VersionResult version = repository.system().resolveVersion(repository.session(), request);
        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(request.getArtifact().setVersion(version.getVersion()));
        ArtifactResult result = repository.system().resolveArtifact(repository.session(), artifactRequest);

        assertEquals("2.0-SNAPSHOT", version.getVersion());
        assertEquals(repository.session().getLocalRepository(), version.getRepository());
        assertTrue(version.getExceptions().isEmpty());
        assertTrue(result.isResolved());
        assertEquals("local development build", Files.readString(result.getArtifact().getFile().toPath()));
    }
}
