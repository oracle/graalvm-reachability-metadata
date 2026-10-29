/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_repository_metadata;

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Plugin;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetadataTest {
    @Test
    void mergesNewerVersionAndSnapshotWithoutDuplicatingVersions() {
        Metadata local = artifact("1.0", "20250101000000");
        Snapshot localSnapshot = new Snapshot();
        localSnapshot.setTimestamp("20250101.000000");
        localSnapshot.setBuildNumber(1);
        local.getVersioning().setSnapshot(localSnapshot);
        Metadata remote = artifact("1.1", "20250102000000");
        remote.getVersioning().addVersion("1.0");
        Snapshot remoteSnapshot = new Snapshot();
        remoteSnapshot.setTimestamp("20250102.000000");
        remoteSnapshot.setBuildNumber(2);
        remoteSnapshot.setLocalCopy(true);
        remote.getVersioning().setSnapshot(remoteSnapshot);

        assertTrue(local.merge(remote));

        Versioning merged = local.getVersioning();
        assertEquals(List.of("1.0", "1.1"), merged.getVersions());
        assertEquals("1.1", merged.getRelease());
        assertEquals("1.1", merged.getLatest());
        assertEquals("20250102000000", merged.getLastUpdated());
        assertSame(localSnapshot, merged.getSnapshot());
        assertEquals("20250102.000000", localSnapshot.getTimestamp());
        assertEquals(2, localSnapshot.getBuildNumber());
        assertTrue(localSnapshot.isLocalCopy());
    }

    @Test
    void addsOlderVersionsWithoutReplacingNewerReleaseAndSnapshot() {
        Metadata local = artifact("1.1", "20250102000000");
        Snapshot snapshot = new Snapshot();
        snapshot.setTimestamp("20250102.000000");
        snapshot.setBuildNumber(2);
        local.getVersioning().setSnapshot(snapshot);
        Metadata older = artifact("1.0", "20250101000000");
        older.getVersioning().setSnapshot(new Snapshot());

        assertTrue(local.merge(older));

        Versioning merged = local.getVersioning();
        assertEquals(List.of("1.1", "1.0"), merged.getVersions());
        assertEquals("1.1", merged.getLatest());
        assertEquals("1.1", merged.getRelease());
        assertEquals("20250102000000", merged.getLastUpdated());
        assertSame(snapshot, merged.getSnapshot());
        assertEquals("20250102.000000", snapshot.getTimestamp());
        assertEquals(2, snapshot.getBuildNumber());
        assertFalse(local.merge(older));
    }

    @Test
    void initializesVersioningAndCopiesSnapshotFromSource() {
        Metadata local = new Metadata();
        Metadata remote = artifact("1.0", "20250102000000");
        Snapshot snapshot = new Snapshot();
        snapshot.setTimestamp("20250102.000000");
        snapshot.setBuildNumber(3);
        remote.getVersioning().setSnapshot(snapshot);

        assertTrue(local.merge(remote));

        Versioning merged = local.getVersioning();
        assertNotSame(remote.getVersioning(), merged);
        assertEquals(List.of("1.0"), merged.getVersions());
        assertEquals("1.0", merged.getRelease());
        assertEquals("1.0", merged.getLatest());
        assertEquals("20250102000000", merged.getLastUpdated());
        assertNotSame(snapshot, merged.getSnapshot());
        assertEquals(snapshot.getTimestamp(), merged.getSnapshot().getTimestamp());
        assertEquals(snapshot.getBuildNumber(), merged.getSnapshot().getBuildNumber());
    }

    @Test
    void mergesPluginMappingsByPrefixAndKeepsExistingMapping() {
        Metadata local = new Metadata();
        Plugin existing = plugin("report", "local-report-plugin", "Local reports");
        local.addPlugin(existing);
        Metadata remote = new Metadata();
        remote.addPlugin(plugin("report", "remote-report-plugin", "Remote reports"));
        Plugin added = plugin("verify", "verify-maven-plugin", "Verify project");
        remote.addPlugin(added);

        assertTrue(local.merge(remote));
        assertEquals(2, local.getPlugins().size());
        assertSame(existing, local.getPlugins().get(0));
        assertEquals("local-report-plugin", existing.getArtifactId());
        Plugin copied = (Plugin) local.getPlugins().get(1);
        assertNotSame(added, copied);
        assertEquals("verify", copied.getPrefix());
        assertEquals("verify-maven-plugin", copied.getArtifactId());
        assertEquals("Verify project", copied.getName());
        assertFalse(local.merge(remote));
    }

    private static Metadata artifact(String version, String lastUpdated) {
        Metadata metadata = new Metadata();
        Versioning versioning = new Versioning();
        versioning.setLatest(version);
        versioning.setRelease(version);
        versioning.setLastUpdated(lastUpdated);
        versioning.addVersion(version);
        metadata.setVersioning(versioning);
        return metadata;
    }

    private static Plugin plugin(String prefix, String artifactId, String name) {
        Plugin plugin = new Plugin();
        plugin.setPrefix(prefix);
        plugin.setArtifactId(artifactId);
        plugin.setName(name);
        return plugin;
    }
}
