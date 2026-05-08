/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_toolchain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.api.toolchain.InputLocation;
import org.apache.maven.api.toolchain.InputSource;
import org.apache.maven.api.toolchain.PersistedToolchains;
import org.apache.maven.api.toolchain.ToolchainModel;
import org.apache.maven.api.toolchain.TrackableBase;
import org.apache.maven.api.xml.XmlNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Maven_api_toolchainTest {
    private static final String TOOLCHAINS_NAMESPACE = "https://maven.apache.org/TOOLCHAINS/1.2.0";

    @Test
    void toolchainModelBuilderCreatesImmutableModelWithConfiguration() {
        Map<String, String> provides = new LinkedHashMap<>();
        provides.put("version", "21");
        provides.put("vendor", "graalvm");
        XmlNode jdkHome = XmlNode.newInstance("jdkHome", "/opt/graalvm-jdk-21");
        XmlNode configuration = XmlNode.newInstance("configuration", List.of(jdkHome));
        InputLocation typeLocation = new InputLocation(7, 13, new InputSource("toolchains.xml"));
        InputLocation importedFrom = new InputLocation(1, 1, new InputSource("imported-toolchains.xml"));

        ToolchainModel toolchain = ToolchainModel.newBuilder()
                .type("jdk")
                .provides(provides)
                .configuration(configuration)
                .location("type", typeLocation)
                .importedFrom(importedFrom)
                .build();
        provides.put("arch", "x86_64");

        assertThat(toolchain.getType()).isEqualTo("jdk");
        assertThat(toolchain.getProvides()).containsExactlyInAnyOrderEntriesOf(
                Map.of("version", "21", "vendor", "graalvm"));
        assertThat(toolchain.getProvides()).doesNotContainKey("arch");
        assertThat(toolchain.getConfiguration()).isSameAs(configuration);
        assertThat(toolchain.getConfiguration().child("jdkHome").value()).isEqualTo("/opt/graalvm-jdk-21");
        assertThat(toolchain.getLocation("type")).isSameAs(typeLocation);
        assertThat(toolchain.getLocationKeys()).containsOnly("type");
        assertThat(toolchain.getImportedFrom()).isSameAs(importedFrom);
        assertThatThrownBy(() -> toolchain.getProvides().put("ignored", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toolchainModelWithMethodsReturnModifiedCopiesWithoutMutatingOriginal() {
        ToolchainModel original = ToolchainModel.newBuilder()
                .type("jdk")
                .provides(Map.of("version", "17"))
                .configuration(XmlNode.newInstance("configuration"))
                .location("type", new InputLocation(2, 5))
                .build();
        XmlNode replacementConfiguration = XmlNode.newBuilder()
                .name("configuration")
                .children(List.of(XmlNode.newInstance("jdkHome", "/opt/jdk-21")))
                .build();

        ToolchainModel updated = original.withType("custom")
                .withProvides(Map.of("version", "21", "vendor", "apache"))
                .withConfiguration(replacementConfiguration);

        assertThat(original.getType()).isEqualTo("jdk");
        assertThat(original.getProvides()).containsExactly(Map.entry("version", "17"));
        assertThat(original.getConfiguration().children()).isEmpty();
        assertThat(updated.getType()).isEqualTo("custom");
        assertThat(updated.getProvides()).containsExactlyInAnyOrderEntriesOf(
                Map.of("version", "21", "vendor", "apache"));
        assertThat(updated.getConfiguration().child("jdkHome").value()).isEqualTo("/opt/jdk-21");
        assertThat(updated.getLocation("type")).isSameAs(original.getLocation("type"));
        assertThat(ToolchainModel.newBuilder(updated).build()).isSameAs(updated);
    }

    @Test
    void toolchainModelEqualityUsesTypeAndProvides() {
        ToolchainModel first = ToolchainModel.newBuilder()
                .type("jdk")
                .provides(Map.of("version", "21"))
                .configuration(XmlNode.newInstance("firstConfiguration"))
                .location("type", new InputLocation(1, 1))
                .build();
        ToolchainModel equalWithDifferentConfigurationAndLocation = ToolchainModel.newBuilder()
                .type("jdk")
                .provides(Map.of("version", "21"))
                .configuration(XmlNode.newInstance("secondConfiguration"))
                .location("type", new InputLocation(99, 3))
                .build();
        ToolchainModel different = first.withProvides(Map.of("version", "17"));

        assertThat(first).isEqualTo(equalWithDifferentConfigurationAndLocation);
        assertThat(first.hashCode()).isEqualTo(equalWithDifferentConfigurationAndLocation.hashCode());
        assertThat(first).isNotEqualTo(different);
        assertThat(first).isNotEqualTo("jdk");
    }

    @Test
    void persistedToolchainsUsesDefaultsAndCopiesToolchainCollection() {
        ToolchainModel jdk17 = ToolchainModel.newBuilder()
                .type("jdk")
                .provides(Map.of("version", "17"))
                .build();
        ToolchainModel jdk21 = jdk17.withProvides(Map.of("version", "21"));
        List<ToolchainModel> sourceToolchains = new ArrayList<>(List.of(jdk17));
        InputLocation toolchainsLocation = new InputLocation(3, 1, new InputSource("toolchains.xml"));

        PersistedToolchains persisted = PersistedToolchains.newBuilder()
                .namespaceUri(TOOLCHAINS_NAMESPACE)
                .toolchains(sourceToolchains)
                .location("toolchains", toolchainsLocation)
                .build();
        sourceToolchains.add(jdk21);

        assertThat(PersistedToolchains.newInstance().getModelEncoding()).isEqualTo("UTF-8");
        assertThat(PersistedToolchains.newInstance().getToolchains()).isEmpty();
        assertThat(persisted.getNamespaceUri()).isEqualTo(TOOLCHAINS_NAMESPACE);
        assertThat(persisted.getModelEncoding()).isEqualTo("UTF-8");
        assertThat(persisted.getToolchains()).containsExactly(jdk17);
        assertThat(persisted.getLocation("toolchains")).isSameAs(toolchainsLocation);
        assertThatThrownBy(() -> persisted.getToolchains().add(jdk21))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void persistedToolchainsCanReplaceToolchainListViaWithMethod() {
        ToolchainModel jdk17 = ToolchainModel.newBuilder()
                .type("jdk")
                .provides(Map.of("version", "17"))
                .build();
        ToolchainModel jdk21 = jdk17.withProvides(Map.of("version", "21"));
        PersistedToolchains original = PersistedToolchains.newBuilder()
                .namespaceUri(TOOLCHAINS_NAMESPACE)
                .modelEncoding("ISO-8859-1")
                .toolchains(List.of(jdk17))
                .build();

        PersistedToolchains updated = original.withToolchains(List.of(jdk17, jdk21));

        assertThat(original.getModelEncoding()).isEqualTo("ISO-8859-1");
        assertThat(original.getToolchains()).containsExactly(jdk17);
        assertThat(updated.getNamespaceUri()).isEqualTo(TOOLCHAINS_NAMESPACE);
        assertThat(updated.getModelEncoding()).isEqualTo("ISO-8859-1");
        assertThat(updated.getToolchains()).containsExactly(jdk17, jdk21);
        assertThat(PersistedToolchains.newBuilder(updated).build()).isSameAs(updated);
    }

    @Test
    void persistedToolchainsWithBuilderUpdatesModelMetadataAndAddsLocations() {
        ToolchainModel jdk = ToolchainModel.newBuilder()
                .type("jdk")
                .provides(Map.of("version", "21"))
                .build();
        InputLocation originalLocation = new InputLocation(2, 1, new InputSource("original-toolchains.xml"));
        InputLocation addedLocation = new InputLocation(5, 3, new InputSource("updated-toolchains.xml"));
        InputLocation importedFrom = new InputLocation(1, 1, new InputSource("imported-toolchains.xml"));
        PersistedToolchains original = PersistedToolchains.newBuilder()
                .namespaceUri(TOOLCHAINS_NAMESPACE)
                .modelEncoding("UTF-8")
                .toolchains(List.of(jdk))
                .location("original", originalLocation)
                .build();

        PersistedToolchains updated = PersistedToolchains.newBuilder(original, true)
                .namespaceUri("https://maven.apache.org/TOOLCHAINS/custom")
                .modelEncoding("UTF-16")
                .location("added", addedLocation)
                .importedFrom(importedFrom)
                .build();

        assertThat(original.getNamespaceUri()).isEqualTo(TOOLCHAINS_NAMESPACE);
        assertThat(original.getModelEncoding()).isEqualTo("UTF-8");
        assertThat(original.getLocation("original")).isSameAs(originalLocation);
        assertThat(original.getLocation("added")).isNull();
        assertThat(original.getImportedFrom()).isNull();
        assertThat(updated.getNamespaceUri()).isEqualTo("https://maven.apache.org/TOOLCHAINS/custom");
        assertThat(updated.getModelEncoding()).isEqualTo("UTF-16");
        assertThat(updated.getToolchains()).containsExactly(jdk);
        assertThat(updated.getLocation("original")).isSameAs(originalLocation);
        assertThat(updated.getLocation("added")).isSameAs(addedLocation);
        assertThat(updated.getImportedFrom()).isSameAs(importedFrom);
    }

    @Test
    void trackableBaseStoresLocationsAndImportedSource() {
        InputLocation typeLocation = new InputLocation(8, 2, new InputSource("toolchains.xml"));
        InputLocation importedFrom = new InputLocation(1, 1, new InputSource("import.xml"));

        TrackableBase trackable = TrackableBase.newBuilder()
                .location("type", typeLocation)
                .location("ignored", null)
                .importedFrom(importedFrom)
                .build();

        assertThat(trackable.getLocation("type")).isSameAs(typeLocation);
        assertThat(trackable.getLocationKeys()).containsOnly("type");
        assertThat(trackable.getImportedFrom()).isSameAs(importedFrom);
        assertThatThrownBy(() -> trackable.getLocationKeys().add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> trackable.getLocation(null))
                .withMessage("key");
        assertThat(TrackableBase.newBuilder(trackable).build()).isSameAs(trackable);
    }

    @Test
    void inputSourceAndLocationExposeCoordinatesAndNestedLocations() {
        InputSource source = new InputSource("file:///home/user/.m2/toolchains.xml");
        InputLocation nested = new InputLocation(12, 4, source);
        InputLocation aggregate = new InputLocation(10, 2, source, Map.of("nested", nested));

        assertThat(source.getLocation()).isEqualTo("file:///home/user/.m2/toolchains.xml");
        assertThat(source).hasToString("file:///home/user/.m2/toolchains.xml");
        assertThat(aggregate.getLineNumber()).isEqualTo(10);
        assertThat(aggregate.getColumnNumber()).isEqualTo(2);
        assertThat(aggregate.getSource()).isSameAs(source);
        assertThat(aggregate.getLocation("nested")).isSameAs(nested);
        assertThat(aggregate.getLocations()).containsExactly(Map.entry("nested", nested));
        assertThatThrownBy(() -> aggregate.getLocations().put("other", nested))
                .isInstanceOf(UnsupportedOperationException.class);

        InputLocation sourceOnly = new InputLocation(source);
        assertThat(sourceOnly.getLineNumber()).isEqualTo(-1);
        assertThat(sourceOnly.getColumnNumber()).isEqualTo(-1);
        assertThat(sourceOnly.getSource()).isSameAs(source);
        assertThat(sourceOnly.getLocation(0)).isSameAs(sourceOnly);
    }

    @Test
    void inputLocationMergeCombinesNestedLocationsAndKeepsPrimaryCoordinates() {
        InputSource source = new InputSource("primary.xml");
        InputLocation primarySegment = new InputLocation(11, 1, source);
        InputLocation primaryReplacement = new InputLocation(12, 1, source);
        InputLocation secondarySegment = new InputLocation(21, 1, new InputSource("secondary.xml"));
        InputLocation secondaryReplacement = new InputLocation(22, 1, new InputSource("secondary.xml"));
        InputLocation primary = new InputLocation(10, 5, source, Map.of(
                "segment", primarySegment,
                "replacement", primaryReplacement));
        InputLocation secondary = new InputLocation(20, 6, secondarySegment.getSource(), Map.of(
                "segment", secondarySegment,
                "replacement", secondaryReplacement));

        InputLocation secondaryWins = InputLocation.merge(primary, secondary, true);
        InputLocation primaryWins = InputLocation.merge(primary, secondary, false);

        assertThat(secondaryWins.getLineNumber()).isEqualTo(10);
        assertThat(secondaryWins.getColumnNumber()).isEqualTo(5);
        assertThat(secondaryWins.getSource()).isSameAs(source);
        assertThat(secondaryWins.getLocation("segment")).isSameAs(secondarySegment);
        assertThat(secondaryWins.getLocation("replacement")).isSameAs(secondaryReplacement);
        assertThat(primaryWins.getLocation("segment")).isSameAs(primarySegment);
        assertThat(primaryWins.getLocation("replacement")).isSameAs(primaryReplacement);
        assertThat(InputLocation.merge(primary, null, true)).isSameAs(primary);
        assertThat(InputLocation.merge(null, secondary, true)).isSameAs(secondary);
    }

    @Test
    void inputLocationMergeCanReorderSelectedSourceAndTargetLocations() {
        InputLocation sourceZero = new InputLocation(1, 1);
        InputLocation sourceOne = new InputLocation(2, 1);
        InputLocation targetZero = new InputLocation(3, 1);
        InputLocation targetOne = new InputLocation(4, 1);
        InputLocation source = new InputLocation(100, 10, new InputSource("source.xml"), Map.of(
                0, sourceZero,
                1, sourceOne));
        InputLocation target = new InputLocation(200, 20, new InputSource("target.xml"), Map.of(
                0, targetZero,
                1, targetOne));

        InputLocation merged = InputLocation.merge(source, target, List.of(0, -2, 1, -1));

        assertThat(merged.getLineNumber()).isEqualTo(100);
        assertThat(merged.getColumnNumber()).isEqualTo(10);
        assertThat(merged.getSource().getLocation()).isEqualTo("source.xml");
        assertThat(merged.getLocation(0)).isSameAs(sourceZero);
        assertThat(merged.getLocation(1)).isSameAs(targetOne);
        assertThat(merged.getLocation(2)).isSameAs(sourceOne);
        assertThat(merged.getLocation(3)).isSameAs(targetZero);
        assertThat(merged.getLocations()).containsOnlyKeys(0, 1, 2, 3);
    }
}
