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
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetadataXpp3WriterTest {
    @Test
    void roundTripsRepositoryMetadataAndEscapesXmlText() throws Exception {
        Metadata metadata = new Metadata();
        metadata.setGroupId("com.example");
        metadata.setArtifactId("sample-tool");
        metadata.setVersion("1.1-SNAPSHOT");
        Versioning versioning = new Versioning();
        versioning.setLatest("1.1-SNAPSHOT");
        versioning.setRelease("1.0");
        versioning.addVersion("1.0");
        versioning.addVersion("1.1-SNAPSHOT");
        versioning.setLastUpdated("20250102030405");
        Snapshot snapshot = new Snapshot();
        snapshot.setTimestamp("20250102.030405");
        snapshot.setBuildNumber(7);
        snapshot.setLocalCopy(true);
        versioning.setSnapshot(snapshot);
        metadata.setVersioning(versioning);
        Plugin plugin = new Plugin();
        plugin.setName("Tools & <Reports>");
        plugin.setPrefix("reports");
        plugin.setArtifactId("reports-maven-plugin");
        metadata.addPlugin(plugin);

        StringWriter output = new StringWriter();
        new MetadataXpp3Writer().write(output, metadata);
        String xml = output.toString();
        assertTrue(xml.contains("&amp;"));
        assertTrue(xml.contains("&lt;"));
        Metadata restored = new MetadataXpp3Reader().read(new StringReader(xml));
        assertEquals(metadata.getGroupId(), restored.getGroupId());
        assertEquals(metadata.getArtifactId(), restored.getArtifactId());
        assertEquals(metadata.getVersion(), restored.getVersion());
        Versioning restoredVersioning = restored.getVersioning();
        assertEquals(versioning.getLatest(), restoredVersioning.getLatest());
        assertEquals(versioning.getRelease(), restoredVersioning.getRelease());
        assertEquals(versioning.getVersions(), restoredVersioning.getVersions());
        assertEquals(versioning.getLastUpdated(), restoredVersioning.getLastUpdated());
        Snapshot restoredSnapshot = restoredVersioning.getSnapshot();
        assertEquals(snapshot.getTimestamp(), restoredSnapshot.getTimestamp());
        assertEquals(snapshot.getBuildNumber(), restoredSnapshot.getBuildNumber());
        assertTrue(restoredSnapshot.isLocalCopy());
        assertEquals(1, restored.getPlugins().size());
        Plugin restoredPlugin = (Plugin) restored.getPlugins().get(0);
        assertEquals(plugin.getName(), restoredPlugin.getName());
        assertEquals(plugin.getPrefix(), restoredPlugin.getPrefix());
        assertEquals(plugin.getArtifactId(), restoredPlugin.getArtifactId());
    }

    @Test
    void omitsUnsetFieldsAndDefaultSnapshotValues() throws Exception {
        Metadata metadata = new Metadata();
        metadata.setGroupId("com.example");
        Versioning versioning = new Versioning();
        versioning.setSnapshot(new Snapshot());
        metadata.setVersioning(versioning);
        StringWriter output = new StringWriter();
        new MetadataXpp3Writer().write(output, metadata);
        String xml = output.toString();
        assertFalse(xml.contains("<plugins>"));
        assertFalse(xml.contains("<versions>"));
        assertFalse(xml.contains("<buildNumber>"));
        assertFalse(xml.contains("<localCopy>"));
        Metadata restored = new MetadataXpp3Reader().read(new StringReader(xml));
        assertEquals("com.example", restored.getGroupId());
        assertNull(restored.getArtifactId());
        assertNull(restored.getVersioning().getRelease());
        assertTrue(restored.getPlugins().isEmpty());
        assertTrue(restored.getVersioning().getVersions().isEmpty());
        assertEquals(0, restored.getVersioning().getSnapshot().getBuildNumber());
        assertFalse(restored.getVersioning().getSnapshot().isLocalCopy());
    }
}
