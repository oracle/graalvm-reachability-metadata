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
import org.sonatype.aether.resolution.VersionRangeRequest;
import org.sonatype.aether.resolution.VersionRangeResult;
import org.sonatype.aether.version.Version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class DefaultVersionRangeResolverTest {
    @TempDir
    Path directory;

    @Test
    void filtersAndSortsInstalledVersionsWithinAnExclusiveUpperBound() throws Exception {
        RepositoryFixture repository = new RepositoryFixture(directory);
        for (String version : List.of("1.10", "1.2", "2.0", "0.9")) {
            repository.install("ranged", "txt", version, "version " + version);
        }
        VersionRangeRequest request = new VersionRangeRequest();
        request.setArtifact(repository.artifact("ranged", "txt", "[1.0,2.0)"));

        VersionRangeResult result = repository.system().resolveVersionRange(repository.session(), request);

        assertEquals(List.of("1.2", "1.10"), result.getVersions().stream().map(Version::toString).toList());
        for (Version version : result.getVersions()) {
            assertTrue(result.getVersionConstraint().containsVersion(version));
            assertEquals(repository.session().getLocalRepository(), result.getRepository(version));
        }
        assertTrue(result.getExceptions().isEmpty());
    }
}
