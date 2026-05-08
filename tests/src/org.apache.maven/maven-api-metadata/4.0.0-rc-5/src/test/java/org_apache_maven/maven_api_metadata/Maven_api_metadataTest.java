/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_metadata;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.api.metadata.Metadata;
import org.apache.maven.api.metadata.Plugin;
import org.apache.maven.api.metadata.Snapshot;
import org.apache.maven.api.metadata.SnapshotVersion;
import org.apache.maven.api.metadata.Versioning;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Maven_api_metadataTest {
    @Test
    void buildsCompleteMetadataModel() {
        Plugin compilerPlugin = Plugin.newBuilder()
                .name("Maven Compiler Plugin")
                .prefix("compiler")
                .artifactId("maven-compiler-plugin")
                .build();
        Snapshot snapshot = Snapshot.newBuilder()
                .timestamp("20240508.120000")
                .buildNumber(7)
                .localCopy(false)
                .build();
        SnapshotVersion sourcesSnapshot = SnapshotVersion.newBuilder()
                .classifier("sources")
                .extension("jar")
                .version("1.0-20240508.120000-7")
                .updated("20240508120000")
                .build();
        Versioning versioning = Versioning.newBuilder()
                .latest("1.1")
                .release("1.0")
                .versions(List.of("1.0", "1.1"))
                .lastUpdated("20240508120000")
                .snapshot(snapshot)
                .snapshotVersions(List.of(sourcesSnapshot))
                .build();

        Metadata metadata = Metadata.newBuilder()
                .namespaceUri("https://maven.apache.org/METADATA/1.1.0")
                .modelEncoding("UTF-16")
                .modelVersion("1.1.0")
                .groupId("org.example")
                .artifactId("demo-library")
                .version("1.0-SNAPSHOT")
                .versioning(versioning)
                .plugins(List.of(compilerPlugin))
                .build();

        assertThat(metadata.getNamespaceUri()).isEqualTo("https://maven.apache.org/METADATA/1.1.0");
        assertThat(metadata.getModelEncoding()).isEqualTo("UTF-16");
        assertThat(metadata.getModelVersion()).isEqualTo("1.1.0");
        assertThat(metadata.getGroupId()).isEqualTo("org.example");
        assertThat(metadata.getArtifactId()).isEqualTo("demo-library");
        assertThat(metadata.getVersion()).isEqualTo("1.0-SNAPSHOT");
        assertThat(metadata.getPlugins()).containsExactly(compilerPlugin);
        assertThat(metadata.getVersioning().getLatest()).isEqualTo("1.1");
        assertThat(metadata.getVersioning().getRelease()).isEqualTo("1.0");
        assertThat(metadata.getVersioning().getVersions()).containsExactly("1.0", "1.1");
        assertThat(metadata.getVersioning().getLastUpdated()).isEqualTo("20240508120000");
        assertThat(metadata.getVersioning().getSnapshot().getTimestamp()).isEqualTo("20240508.120000");
        assertThat(metadata.getVersioning().getSnapshot().getBuildNumber()).isEqualTo(7);
        assertThat(metadata.getVersioning().getSnapshot().isLocalCopy()).isFalse();
        assertThat(metadata.getVersioning().getSnapshotVersions()).containsExactly(sourcesSnapshot);
    }

    @Test
    void defaultInstancesExposeEmptyCollectionsAndPrimitiveDefaults() {
        Metadata metadata = Metadata.newInstance();
        Versioning versioning = Versioning.newInstance();
        Snapshot snapshot = Snapshot.newInstance();
        Plugin plugin = Plugin.newInstance();
        SnapshotVersion snapshotVersion = SnapshotVersion.newInstance();

        assertThat(metadata.getModelEncoding()).isEqualTo("UTF-8");
        assertThat(metadata.getNamespaceUri()).isNull();
        assertThat(metadata.getModelVersion()).isNull();
        assertThat(metadata.getGroupId()).isNull();
        assertThat(metadata.getArtifactId()).isNull();
        assertThat(metadata.getVersion()).isNull();
        assertThat(metadata.getVersioning()).isNull();
        assertThat(metadata.getPlugins()).isEmpty();

        assertThat(versioning.getLatest()).isNull();
        assertThat(versioning.getRelease()).isNull();
        assertThat(versioning.getVersions()).isEmpty();
        assertThat(versioning.getLastUpdated()).isNull();
        assertThat(versioning.getSnapshot()).isNull();
        assertThat(versioning.getSnapshotVersions()).isEmpty();

        assertThat(snapshot.getTimestamp()).isNull();
        assertThat(snapshot.getBuildNumber()).isZero();
        assertThat(snapshot.isLocalCopy()).isFalse();

        assertThat(plugin.getName()).isNull();
        assertThat(plugin.getPrefix()).isNull();
        assertThat(plugin.getArtifactId()).isNull();

        assertThat(snapshotVersion.getClassifier()).isNull();
        assertThat(snapshotVersion.getExtension()).isNull();
        assertThat(snapshotVersion.getVersion()).isNull();
        assertThat(snapshotVersion.getUpdated()).isNull();
    }

    @Test
    void collectionInputsAreDefensivelyCopiedAndExposedAsImmutable() {
        Plugin cleanPlugin = Plugin.newBuilder()
                .name("Maven Clean Plugin")
                .prefix("clean")
                .artifactId("maven-clean-plugin")
                .build();
        Plugin deployPlugin = Plugin.newBuilder()
                .name("Maven Deploy Plugin")
                .prefix("deploy")
                .artifactId("maven-deploy-plugin")
                .build();
        List<Plugin> plugins = new ArrayList<>();
        plugins.add(cleanPlugin);

        Metadata metadata = Metadata.newBuilder()
                .plugins(plugins)
                .build();
        plugins.add(deployPlugin);

        assertThat(metadata.getPlugins()).containsExactly(cleanPlugin);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> metadata.getPlugins().add(deployPlugin));

        SnapshotVersion jarSnapshot = SnapshotVersion.newBuilder()
                .extension("jar")
                .version("1.0-20240508.120000-1")
                .updated("20240508120000")
                .build();
        SnapshotVersion pomSnapshot = SnapshotVersion.newBuilder()
                .extension("pom")
                .version("1.0-20240508.120000-1")
                .updated("20240508120000")
                .build();
        List<String> versions = new ArrayList<>(List.of("1.0"));
        List<SnapshotVersion> snapshotVersions = new ArrayList<>(List.of(jarSnapshot));

        Versioning versioning = Versioning.newBuilder()
                .versions(versions)
                .snapshotVersions(snapshotVersions)
                .build();
        versions.add("1.1");
        snapshotVersions.add(pomSnapshot);

        assertThat(versioning.getVersions()).containsExactly("1.0");
        assertThat(versioning.getSnapshotVersions()).containsExactly(jarSnapshot);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> versioning.getVersions().add("2.0"));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> versioning.getSnapshotVersions().add(pomSnapshot));
    }

    @Test
    void withMethodsCreateModifiedCopiesWithoutMutatingOriginals() {
        Plugin originalPlugin = Plugin.newBuilder()
                .name("Original")
                .prefix("original")
                .artifactId("original-plugin")
                .build();
        Plugin updatedPlugin = originalPlugin
                .withName("Updated")
                .withPrefix("updated")
                .withArtifactId("updated-plugin");

        assertThat(originalPlugin.getName()).isEqualTo("Original");
        assertThat(originalPlugin.getPrefix()).isEqualTo("original");
        assertThat(originalPlugin.getArtifactId()).isEqualTo("original-plugin");
        assertThat(updatedPlugin.getName()).isEqualTo("Updated");
        assertThat(updatedPlugin.getPrefix()).isEqualTo("updated");
        assertThat(updatedPlugin.getArtifactId()).isEqualTo("updated-plugin");

        Snapshot originalSnapshot = Snapshot.newBuilder()
                .timestamp("20240508.120000")
                .buildNumber(1)
                .localCopy(false)
                .build();
        Snapshot updatedSnapshot = originalSnapshot
                .withTimestamp("20240509.130000")
                .withBuildNumber(2)
                .withLocalCopy(true);

        assertThat(originalSnapshot.getTimestamp()).isEqualTo("20240508.120000");
        assertThat(originalSnapshot.getBuildNumber()).isEqualTo(1);
        assertThat(originalSnapshot.isLocalCopy()).isFalse();
        assertThat(updatedSnapshot.getTimestamp()).isEqualTo("20240509.130000");
        assertThat(updatedSnapshot.getBuildNumber()).isEqualTo(2);
        assertThat(updatedSnapshot.isLocalCopy()).isTrue();

        SnapshotVersion originalSnapshotVersion = SnapshotVersion.newBuilder()
                .classifier("sources")
                .extension("jar")
                .version("1.0-20240508.120000-1")
                .updated("20240508120000")
                .build();
        SnapshotVersion updatedSnapshotVersion = originalSnapshotVersion
                .withClassifier("javadoc")
                .withExtension("zip")
                .withVersion("1.0-20240509.130000-2")
                .withUpdated("20240509130000");

        assertThat(originalSnapshotVersion.getClassifier()).isEqualTo("sources");
        assertThat(originalSnapshotVersion.getExtension()).isEqualTo("jar");
        assertThat(originalSnapshotVersion.getVersion()).isEqualTo("1.0-20240508.120000-1");
        assertThat(originalSnapshotVersion.getUpdated()).isEqualTo("20240508120000");
        assertThat(updatedSnapshotVersion.getClassifier()).isEqualTo("javadoc");
        assertThat(updatedSnapshotVersion.getExtension()).isEqualTo("zip");
        assertThat(updatedSnapshotVersion.getVersion()).isEqualTo("1.0-20240509.130000-2");
        assertThat(updatedSnapshotVersion.getUpdated()).isEqualTo("20240509130000");
    }

    @Test
    void metadataAndVersioningWithMethodsPreserveUntouchedState() {
        Snapshot snapshot = Snapshot.newBuilder()
                .timestamp("20240508.120000")
                .buildNumber(1)
                .build();
        SnapshotVersion snapshotVersion = SnapshotVersion.newBuilder()
                .extension("jar")
                .version("1.0-20240508.120000-1")
                .updated("20240508120000")
                .build();
        Versioning originalVersioning = Versioning.newBuilder()
                .latest("1.0")
                .release("1.0")
                .versions(List.of("1.0"))
                .lastUpdated("20240508120000")
                .snapshot(snapshot)
                .snapshotVersions(List.of(snapshotVersion))
                .build();
        Versioning updatedVersioning = originalVersioning
                .withLatest("1.1")
                .withRelease("1.1")
                .withVersions(List.of("1.0", "1.1"))
                .withLastUpdated("20240509130000")
                .withSnapshot(snapshot.withBuildNumber(2))
                .withSnapshotVersions(List.of(snapshotVersion.withExtension("pom")));

        assertThat(originalVersioning.getLatest()).isEqualTo("1.0");
        assertThat(originalVersioning.getRelease()).isEqualTo("1.0");
        assertThat(originalVersioning.getVersions()).containsExactly("1.0");
        assertThat(originalVersioning.getLastUpdated()).isEqualTo("20240508120000");
        assertThat(originalVersioning.getSnapshot().getBuildNumber()).isEqualTo(1);
        assertThat(originalVersioning.getSnapshotVersions()).containsExactly(snapshotVersion);
        assertThat(updatedVersioning.getLatest()).isEqualTo("1.1");
        assertThat(updatedVersioning.getRelease()).isEqualTo("1.1");
        assertThat(updatedVersioning.getVersions()).containsExactly("1.0", "1.1");
        assertThat(updatedVersioning.getLastUpdated()).isEqualTo("20240509130000");
        assertThat(updatedVersioning.getSnapshot().getBuildNumber()).isEqualTo(2);
        assertThat(updatedVersioning.getSnapshotVersions())
                .extracting(SnapshotVersion::getExtension)
                .containsExactly("pom");

        Plugin plugin = Plugin.newBuilder().artifactId("maven-install-plugin").build();
        Metadata originalMetadata = Metadata.newBuilder()
                .modelVersion("1.1.0")
                .groupId("org.example")
                .artifactId("demo")
                .version("1.0")
                .versioning(originalVersioning)
                .plugins(List.of(plugin))
                .build();
        Metadata updatedMetadata = originalMetadata
                .withModelVersion("1.2.0")
                .withGroupId("com.example")
                .withArtifactId("demo-new")
                .withVersion("1.1")
                .withVersioning(updatedVersioning)
                .withPlugins(List.of(plugin.withArtifactId("maven-deploy-plugin")));

        assertThat(originalMetadata.getModelVersion()).isEqualTo("1.1.0");
        assertThat(originalMetadata.getGroupId()).isEqualTo("org.example");
        assertThat(originalMetadata.getArtifactId()).isEqualTo("demo");
        assertThat(originalMetadata.getVersion()).isEqualTo("1.0");
        assertThat(originalMetadata.getVersioning()).isSameAs(originalVersioning);
        assertThat(originalMetadata.getPlugins()).containsExactly(plugin);
        assertThat(updatedMetadata.getModelVersion()).isEqualTo("1.2.0");
        assertThat(updatedMetadata.getGroupId()).isEqualTo("com.example");
        assertThat(updatedMetadata.getArtifactId()).isEqualTo("demo-new");
        assertThat(updatedMetadata.getVersion()).isEqualTo("1.1");
        assertThat(updatedMetadata.getVersioning()).isSameAs(updatedVersioning);
        assertThat(updatedMetadata.getPlugins())
                .extracting(Plugin::getArtifactId)
                .containsExactly("maven-deploy-plugin");
    }

    @Test
    void buildersCanBeCreatedFromExistingInstances() {
        Plugin plugin = Plugin.newBuilder()
                .name("Maven Site Plugin")
                .prefix("site")
                .artifactId("maven-site-plugin")
                .build();
        Plugin unchangedPlugin = plugin.with().build();
        Plugin renamedPlugin = Plugin.newBuilder(plugin)
                .name("Renamed Site Plugin")
                .build();

        assertThat(unchangedPlugin).isSameAs(plugin);
        assertThat(renamedPlugin.getName()).isEqualTo("Renamed Site Plugin");
        assertThat(renamedPlugin.getPrefix()).isEqualTo("site");
        assertThat(renamedPlugin.getArtifactId()).isEqualTo("maven-site-plugin");

        Metadata metadata = Metadata.newBuilder()
                .groupId("org.example")
                .artifactId("demo")
                .plugins(List.of(plugin))
                .build();
        Metadata unchangedMetadata = Metadata.newBuilder(metadata).build();
        Metadata copiedMetadata = Metadata.newBuilder(metadata, true).build();

        assertThat(unchangedMetadata).isSameAs(metadata);
        assertThat(copiedMetadata).isNotSameAs(metadata);
        assertThat(copiedMetadata.getGroupId()).isEqualTo("org.example");
        assertThat(copiedMetadata.getArtifactId()).isEqualTo("demo");
        assertThat(copiedMetadata.getPlugins()).containsExactly(plugin);
    }

    @Test
    void snapshotVersionUsesValueEquality() {
        SnapshotVersion first = SnapshotVersion.newBuilder()
                .classifier("sources")
                .extension("jar")
                .version("1.0-20240508.120000-1")
                .updated("20240508120000")
                .build();
        SnapshotVersion sameValues = SnapshotVersion.newBuilder()
                .classifier("sources")
                .extension("jar")
                .version("1.0-20240508.120000-1")
                .updated("20240508120000")
                .build();
        SnapshotVersion differentClassifier = sameValues.withClassifier("javadoc");

        assertThat(first).isEqualTo(sameValues);
        assertThat(first.hashCode()).isEqualTo(sameValues.hashCode());
        assertThat(first).isNotEqualTo(differentClassifier);
        assertThat(first).isNotEqualTo(null);
        assertThat(first).isNotEqualTo("1.0-20240508.120000-1");
    }
}
