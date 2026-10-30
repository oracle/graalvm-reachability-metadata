/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_metadata;

import org.apache.maven.api.metadata.Metadata;
import org.apache.maven.api.metadata.Plugin;
import org.apache.maven.api.metadata.Versioning;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetadataTest {
    @Test
    void buildsArtifactMetadataAndDerivesAnIndependentUpdate() {
        Versioning versioning = Versioning.newBuilder().latest("2.0-SNAPSHOT").release("1.0").build();
        Metadata original = Metadata.newBuilder()
                .namespaceUri("http://maven.apache.org/METADATA/1.1.0")
                .modelEncoding("UTF-8")
                .modelVersion("1.1.0")
                .groupId("example.tools")
                .artifactId("sample")
                .version("2.0-SNAPSHOT")
                .versioning(versioning)
                .build();
        Metadata updated = original.with().artifactId("sample-tools").build();

        assertEquals("example.tools", updated.getGroupId());
        assertEquals("sample-tools", updated.getArtifactId());
        assertEquals("2.0-SNAPSHOT", updated.getVersion());
        assertEquals("1.0", updated.getVersioning().getRelease());
        assertEquals("1.1.0", updated.getModelVersion());
        assertEquals("UTF-8", updated.getModelEncoding());
        assertEquals(original.getNamespaceUri(), updated.getNamespaceUri());
        assertEquals("sample", original.getArtifactId());
        assertTrue(original.getPlugins().isEmpty());
    }

    @Test
    void pluginMappingsAreDefensivelyCopiedAndReadOnly() {
        Plugin compiler = Plugin.newBuilder().prefix("compile").artifactId("sample-compiler-plugin").build();
        Plugin reports = Plugin.newBuilder().prefix("report").artifactId("sample-report-plugin").build();
        List<Plugin> plugins = new ArrayList<>(List.of(compiler));
        Metadata original = Metadata.newBuilder().groupId("example.tools").plugins(plugins).build();
        plugins.add(reports);
        Metadata updated = original.withPlugins(plugins);
        plugins.clear();

        assertEquals(List.of(compiler), original.getPlugins());
        assertEquals(List.of(compiler, reports), updated.getPlugins());
        assertEquals("example.tools", updated.getGroupId());
        assertThrows(UnsupportedOperationException.class, () -> updated.getPlugins().clear());
        assertTrue(updated.withPlugins(List.of()).getPlugins().isEmpty());
    }

    @Test
    void fluentCopiesPreserveTheOriginalCoordinates() {
        Metadata original = Metadata.newInstance().withGroupId("example.original")
                .withArtifactId("sample").withVersion("1.0-SNAPSHOT").withModelVersion("1.1.0");
        Versioning versioning = Versioning.newBuilder().latest("2.0-SNAPSHOT").build();
        Metadata updated = Metadata.newBuilder(original, true).build()
                .withGroupId("example.updated").withArtifactId("updated-sample")
                .withVersion("2.0-SNAPSHOT").withModelVersion("1.0.0").withVersioning(versioning);

        assertEquals("example.updated", updated.getGroupId());
        assertEquals("updated-sample", updated.getArtifactId());
        assertEquals("2.0-SNAPSHOT", updated.getVersion());
        assertEquals("1.0.0", updated.getModelVersion());
        assertEquals("2.0-SNAPSHOT", updated.getVersioning().getLatest());
        assertEquals("example.original", original.getGroupId());
        assertEquals("sample", original.getArtifactId());
        assertEquals("1.0-SNAPSHOT", original.getVersion());
        assertEquals("1.1.0", original.getModelVersion());
    }
}
