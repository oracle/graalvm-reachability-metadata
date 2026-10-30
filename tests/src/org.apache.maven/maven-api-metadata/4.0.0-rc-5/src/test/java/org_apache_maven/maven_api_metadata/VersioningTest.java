/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_metadata;

import org.apache.maven.api.metadata.Snapshot;
import org.apache.maven.api.metadata.SnapshotVersion;
import org.apache.maven.api.metadata.Versioning;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VersioningTest {
    @Test
    void buildsReleaseAndSnapshotVersionInformation() {
        Snapshot snapshot = Snapshot.newBuilder().timestamp("20250120.101112").buildNumber(7).build();
        SnapshotVersion sources = SnapshotVersion.newBuilder().classifier("sources").extension("jar")
                .version("2.0-20250120.101112-7").updated("20250120101112").build();
        Versioning original = Versioning.newBuilder().latest("2.0-SNAPSHOT").release("1.0")
                .versions(List.of("1.0", "2.0-SNAPSHOT")).lastUpdated("20250120101112")
                .snapshot(snapshot).snapshotVersions(List.of(sources)).build();
        Versioning updated = original.with().latest("2.0").release("2.0").build();

        assertEquals("2.0", updated.getLatest());
        assertEquals("2.0", updated.getRelease());
        assertEquals(List.of("1.0", "2.0-SNAPSHOT"), updated.getVersions());
        assertEquals("20250120101112", updated.getLastUpdated());
        assertEquals(7, updated.getSnapshot().getBuildNumber());
        assertEquals(List.of(sources), updated.getSnapshotVersions());
        assertEquals("2.0-SNAPSHOT", original.getLatest());
        assertEquals("1.0", original.getRelease());
    }

    @Test
    void availableVersionsAndSubArtifactsAreImmutableSnapshotsOfTheirInputs() {
        List<String> versions = new ArrayList<>(List.of("1.0", "1.1", "2.0-SNAPSHOT"));
        SnapshotVersion main = SnapshotVersion.newBuilder().extension("jar")
                .version("2.0-20250120.101112-7").build();
        SnapshotVersion sources = main.withClassifier("sources");
        List<SnapshotVersion> artifacts = new ArrayList<>(List.of(main, sources));
        Versioning versioning = Versioning.newBuilder().versions(versions).snapshotVersions(artifacts).build();
        versions.clear();
        artifacts.clear();

        assertEquals(List.of("1.0", "1.1", "2.0-SNAPSHOT"), versioning.getVersions());
        assertEquals(List.of(main, sources), versioning.getSnapshotVersions());
        assertThrows(UnsupportedOperationException.class, () -> versioning.getVersions().add("3.0"));
        assertThrows(UnsupportedOperationException.class, () -> versioning.getSnapshotVersions().clear());
        assertTrue(Versioning.newInstance().getVersions().isEmpty());
        assertTrue(Versioning.newInstance().getSnapshotVersions().isEmpty());
    }

    @Test
    void fluentCopiesReplaceVersionListsAndDeploymentDetailsIndependently() {
        Snapshot previous = Snapshot.newBuilder().timestamp("20250120.101112").buildNumber(7).build();
        Snapshot next = previous.withBuildNumber(8).withTimestamp("20250121.121314");
        SnapshotVersion artifact = SnapshotVersion.newBuilder().extension("jar")
                .version("2.0-20250121.121314-8").build();
        Versioning original = Versioning.newBuilder(false).latest("1.0").release("1.0")
                .versions(List.of("1.0")).snapshot(previous).lastUpdated("20250120101112").build();
        Versioning updated = Versioning.newBuilder(original, true).build()
                .withLatest("2.0-SNAPSHOT").withRelease("1.1")
                .withVersions(List.of("1.0", "1.1", "2.0-SNAPSHOT"))
                .withSnapshot(next).withSnapshotVersions(List.of(artifact)).withLastUpdated("20250121121314");

        assertEquals("2.0-SNAPSHOT", updated.getLatest());
        assertEquals("1.1", updated.getRelease());
        assertEquals(List.of("1.0", "1.1", "2.0-SNAPSHOT"), updated.getVersions());
        assertEquals(8, updated.getSnapshot().getBuildNumber());
        assertEquals(List.of(artifact), updated.getSnapshotVersions());
        assertEquals("20250121121314", updated.getLastUpdated());
        assertEquals(List.of("1.0"), original.getVersions());
        assertEquals(7, original.getSnapshot().getBuildNumber());
        assertTrue(original.getSnapshotVersions().isEmpty());
        assertEquals("20250120101112", original.getLastUpdated());
    }
}
