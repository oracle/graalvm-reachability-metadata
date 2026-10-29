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
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetadataXpp3ReaderTest {
    @Test
    void readsArtifactVersionsSnapshotAndPluginMapping() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <groupId> com.example.tools </groupId>
                  <artifactId>sample-tool</artifactId>
                  <version>1.1-SNAPSHOT</version>
                  <versioning>
                    <latest>1.1-SNAPSHOT</latest>
                    <release>1.0</release>
                    <snapshot>
                      <timestamp>20250102.030405</timestamp>
                      <buildNumber>7</buildNumber>
                      <localCopy>true</localCopy>
                    </snapshot>
                    <versions>
                      <version>1.0</version>
                      <version>1.1-SNAPSHOT</version>
                    </versions>
                    <lastUpdated>20250102030405</lastUpdated>
                  </versioning>
                  <plugins>
                    <plugin>
                      <name>Sample &amp; Tools</name>
                      <prefix>sample</prefix>
                      <artifactId>sample-maven-plugin</artifactId>
                    </plugin>
                  </plugins>
                </metadata>
                """;
        Metadata metadata = new MetadataXpp3Reader().read(new StringReader(xml));

        assertEquals("com.example.tools", metadata.getGroupId());
        assertEquals("sample-tool", metadata.getArtifactId());
        assertEquals("1.1-SNAPSHOT", metadata.getVersion());
        Versioning versioning = metadata.getVersioning();
        assertEquals("1.1-SNAPSHOT", versioning.getLatest());
        assertEquals("1.0", versioning.getRelease());
        assertEquals(2, versioning.getVersions().size());
        assertEquals("1.0", versioning.getVersions().get(0));
        assertEquals("1.1-SNAPSHOT", versioning.getVersions().get(1));
        assertEquals("20250102030405", versioning.getLastUpdated());
        Snapshot snapshot = versioning.getSnapshot();
        assertEquals("20250102.030405", snapshot.getTimestamp());
        assertEquals(7, snapshot.getBuildNumber());
        assertTrue(snapshot.isLocalCopy());
        assertEquals(1, metadata.getPlugins().size());
        Plugin plugin = (Plugin) metadata.getPlugins().get(0);
        assertEquals("Sample & Tools", plugin.getName());
        assertEquals("sample", plugin.getPrefix());
        assertEquals("sample-maven-plugin", plugin.getArtifactId());
    }

    @Test
    void expandsDefaultXhtmlEntities() throws Exception {
        MetadataXpp3Reader reader = new MetadataXpp3Reader();
        assertTrue(reader.getAddDefaultEntities());
        Metadata metadata = reader.read(new StringReader("""
                <metadata><plugins><plugin>
                  <name>Caf&eacute; &copy;</name>
                  <prefix>cafe</prefix>
                </plugin></plugins></metadata>
                """));
        Plugin plugin = (Plugin) metadata.getPlugins().get(0);
        assertEquals("Caf\u00e9 \u00a9", plugin.getName());
    }

    @Test
    void readsStandardXmlWithDefaultEntitiesDisabled() throws Exception {
        MetadataXpp3Reader reader = new MetadataXpp3Reader();
        reader.setAddDefaultEntities(false);
        assertFalse(reader.getAddDefaultEntities());
        Metadata metadata = reader.read(new StringReader("""
                <metadata><artifactId>sample&amp;tool</artifactId></metadata>
                """));
        assertEquals("sample&tool", metadata.getArtifactId());
    }

    @Test
    void rejectsUnknownElementsInStrictModeButAcceptsThemInLenientMode() throws Exception {
        String xml = """
                <metadata><extra>extension</extra><artifactId>sample-tool</artifactId></metadata>
                """;
        MetadataXpp3Reader reader = new MetadataXpp3Reader();
        assertThrows(XmlPullParserException.class, () -> reader.read(new StringReader(xml), true));
        Metadata metadata = reader.read(new StringReader(xml), false);
        assertEquals("sample-tool", metadata.getArtifactId());
    }

    @Test
    void rejectsDuplicateFieldsAndInvalidSnapshotBuildNumbers() {
        MetadataXpp3Reader reader = new MetadataXpp3Reader();
        assertThrows(XmlPullParserException.class, () -> reader.read(new StringReader("""
                <metadata><groupId>com.example</groupId><groupId>com.other</groupId></metadata>
                """)));
        assertThrows(XmlPullParserException.class, () -> reader.read(new StringReader("""
                <metadata><versioning><snapshot><buildNumber>invalid</buildNumber></snapshot></versioning></metadata>
                """)));
    }
}
