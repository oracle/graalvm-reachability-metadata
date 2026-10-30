/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// `splitTestOnlyMetadata` on resource bundles and their `.properties` lookups: those a test
/// resource provides move out of shipped metadata, a library's own stay.
class SplitTestResourceBundleTests extends MetadataTaskTestSupport {
    @Test
    void movesBundleWhoseBasePropertiesIsTestResource() throws IOException {
        assertBundlesSplit(List.of("LocalizerFallback.properties"), List.of("LocalizerFallback"), List.of());
    }

    @Test
    void movesBundleProvidedOnlyByLocaleVariant() throws IOException {
        assertBundlesSplit(List.of("catalog/Messages_de.properties"), List.of("catalog.Messages"), List.of());
    }

    @Test
    void movesBundleNamingTestPackageClass() throws IOException {
        assertBundlesSplit(List.of(), List.of("com_example.lib.LibMessages"), List.of());
    }

    @Test
    void keepsBundleNothingProvides() throws IOException {
        assertBundlesSplit(
                List.of("LocalizerFallback.properties"),
                List.of("LocalizerFallback"),
                List.of("deployment.LocalizerFallback", "catalog.missing.UnavailableMessages")
        );
    }

    @Test
    void keepsLibraryBundleSharingTestResourceName() throws IOException {
        assertBundlesSplit(
                List.of("Messages.properties"),
                List.of("Messages"),
                List.of("com.sun.xml.bind.marshaller.Messages")
        );
    }

    @Test
    void keepsAllowedPackageBundleWhoseTranslationIsTestResource() throws IOException {
        assertBundlesSplit(List.of("com/example/Messages_fr.properties"), List.of(), List.of("com.example.Messages"));
    }

    @Test
    void keepsBundleWhoseSiblingIsNotLocaleVariant() throws IOException {
        assertBundlesSplit(List.of("Messages_extra.properties"), List.of(), List.of("Messages"));
    }

    @Test
    void movesLocaleLookupsOfBundleTestResourceProvides() throws IOException {
        assertGlobsSplit(
                List.of("catalog/Messages.properties"),
                List.of("catalog/Messages_en.properties", "catalog/Messages_en_US.properties"),
                List.of()
        );
    }

    @Test
    void movesBaseLookupOfBundleProvidedOnlyByLocaleVariant() throws IOException {
        assertGlobsSplit(List.of("catalog/Messages_de.properties"), List.of("catalog/Messages.properties"), List.of());
    }

    @Test
    void movesLocaleLookupsOfTestPackageBundleClass() throws IOException {
        assertGlobsSplit(
                List.of(),
                List.of("com_example/lib/LibMessages_en.properties", "com_example/lib/LibMessages_en_US.properties"),
                List.of("com/example/LibMessages_en.properties")
        );
    }

    @Test
    void keepsLocaleLookupsOfBundlesTestResourcesDoNotProvide() throws IOException {
        assertGlobsSplit(
                List.of("catalog/Messages.properties", "com/example/Messages_fr.properties"),
                List.of(),
                List.of(
                        "catalog/Other_en.properties",
                        "Messages_en.properties",
                        "catalog/Messages_extra.properties",
                        "com/example/Messages_en.properties"
                )
        );
    }

    @Test
    void retainsMovedBundleOnSecondRun() throws IOException {
        List<String> movedBundles = List.of("LocalizerFallback");
        List<String> keptBundles = List.of("deployment.LocalizerFallback");
        assertBundlesSplit(List.of("LocalizerFallback.properties"), movedBundles, keptBundles);

        runSplitTask("com.example:lib:1.0.0");

        assertThat(bundleNames(LIB_SHIPPED_METADATA)).containsExactlyElementsOf(keptBundles);
        assertThat(bundleNames(LIB_TEST_METADATA)).containsExactlyElementsOf(movedBundles);
    }

    /// Writes a `com.example:lib:1.0.0` project with `testResources` under `src/test/resources`
    /// and one bundle entry per name, splits it, and checks which bundles moved and which stayed.
    private void assertBundlesSplit(
            List<String> testResources,
            List<String> movedBundles,
            List<String> keptBundles
    ) throws IOException {
        writeLibTestProject();
        for (String resource : testResources) {
            writeSource("tests/src/com.example/lib/1.0.0/src/test/resources/" + resource, "key=value\n");
        }
        String bundleEntries = Stream.concat(movedBundles.stream(), keptBundles.stream())
                .map(this::bundleEntry)
                .collect(Collectors.joining(", "));
        writeJson(LIB_SHIPPED_METADATA, """
                {
                  "resources": [%s]
                }
                """.formatted(bundleEntries));

        runSplitTask("com.example:lib:1.0.0");

        assertThat(bundleNames(LIB_SHIPPED_METADATA)).containsExactlyElementsOf(keptBundles);
        assertThat(bundleNames(LIB_TEST_METADATA)).containsExactlyElementsOf(movedBundles);
    }

    /// Writes a `com.example:lib:1.0.0` project with `testResources` under `src/test/resources`
    /// and one resource entry per glob, splits it, and checks which globs moved and which stayed.
    private void assertGlobsSplit(
            List<String> testResources,
            List<String> movedGlobs,
            List<String> keptGlobs
    ) throws IOException {
        writeLibTestProject();
        for (String resource : testResources) {
            writeSource("tests/src/com.example/lib/1.0.0/src/test/resources/" + resource, "key=value\n");
        }
        String globEntries = Stream.concat(movedGlobs.stream(), keptGlobs.stream())
                .map(this::propertiesResourceEntry)
                .collect(Collectors.joining(", "));
        writeJson(LIB_SHIPPED_METADATA, """
                {
                  "resources": [%s]
                }
                """.formatted(globEntries));

        runSplitTask("com.example:lib:1.0.0");

        assertThat(resourceValues(LIB_SHIPPED_METADATA, "glob")).containsExactlyElementsOf(keptGlobs);
        assertThat(resourceValues(LIB_TEST_METADATA, "glob")).containsExactlyElementsOf(movedGlobs);
    }

    /// The bundle names in `metadataFile`'s `resources`, none when the split did not write the file.
    private List<String> bundleNames(String metadataFile) throws IOException {
        return resourceValues(metadataFile, "bundle");
    }

    /// The `key` values in `metadataFile`'s `resources`, none when the split did not write the file.
    private List<String> resourceValues(String metadataFile, String key) throws IOException {
        List<String> values = new ArrayList<>();
        if (Files.isRegularFile(tempDir.resolve(metadataFile))) {
            readJson(metadataFile).path("resources").forEach(entry -> values.add(entry.get(key).asText()));
        }
        return values;
    }

    /// A resource bundle entry as the agent records it when a library looks the bundle up.
    private String bundleEntry(String bundle) {
        return """
                {
                  "condition": {
                    "typeReached": "com.example.Localizer"
                  },
                  "bundle": "%s"
                }
                """.formatted(bundle);
    }

    /// A resource entry as the agent records it when a `ResourceBundle` lookup reads a `.properties` file.
    private String propertiesResourceEntry(String glob) {
        return """
                {
                  "condition": {
                    "typeReached": "com.example.Localizer"
                  },
                  "glob": "%s"
                }
                """.formatted(glob);
    }
}
