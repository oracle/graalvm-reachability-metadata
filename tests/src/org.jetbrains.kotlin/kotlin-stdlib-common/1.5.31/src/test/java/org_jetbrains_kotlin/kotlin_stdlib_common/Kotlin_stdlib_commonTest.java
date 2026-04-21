/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_stdlib_common;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Kotlin_stdlib_commonTest {
    private static final String MODULE_RESOURCE = "META-INF/kotlin-stdlib-common.kotlin_module";

    // `kotlin-stdlib-common` is a metadata-only artifact, so its public surface is published as
    // Kotlin metadata resources instead of JVM bytecode classes.
    private static final List<String> REPRESENTATIVE_PUBLIC_API_METADATA = List.of(
            "kotlin/Pair.kotlin_metadata",
            "kotlin/Lazy.kotlin_metadata",
            "kotlin/collections/CollectionsKt.kotlin_metadata",
            "kotlin/collections/ArrayDeque.kotlin_metadata",
            "kotlin/coroutines/CoroutineContext.kotlin_metadata",
            "kotlin/random/Random.kotlin_metadata",
            "kotlin/ranges/RangesKt.kotlin_metadata",
            "kotlin/text/Regex.kotlin_metadata"
    );

    @Test
    void providesKotlinModuleDescriptor() throws IOException {
        byte[] moduleBytes = readRequiredResource(MODULE_RESOURCE);

        assertThat(moduleBytes.length)
                .as("the Kotlin module descriptor should be packaged and non-trivial")
                .isGreaterThan(32);
    }

    @Test
    void providesRepresentativeMetadataForCorePublicApis() throws IOException {
        Map<String, Integer> resourceSizes = new LinkedHashMap<>();

        for (String resource : REPRESENTATIVE_PUBLIC_API_METADATA) {
            resourceSizes.put(resource, readRequiredResource(resource).length);
        }

        assertThat(resourceSizes)
                .as("representative public API metadata should be available across core packages")
                .hasSize(REPRESENTATIVE_PUBLIC_API_METADATA.size())
                .allSatisfy((resource, size) -> assertThat(size)
                        .as("resource %s should not be empty", resource)
                        .isGreaterThan(32));
        assertThat(resourceSizes.values().stream().distinct().count())
                .as("these resources should represent distinct API declarations, not placeholders")
                .isGreaterThan(3);
    }

    @Test
    void providesArtifactManifestWithoutVersionPinnedAssertions() throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> manifests = classLoader.getResources("META-INF/MANIFEST.MF");
        Attributes kotlinStdlibCommonAttributes = null;

        while (manifests.hasMoreElements()) {
            URL manifestUrl = manifests.nextElement();
            try (InputStream inputStream = manifestUrl.openStream()) {
                Attributes attributes = new Manifest(inputStream).getMainAttributes();
                if ("kotlin-stdlib-common".equals(attributes.getValue("Implementation-Title"))) {
                    kotlinStdlibCommonAttributes = attributes;
                    break;
                }
            }
        }

        assertThat(kotlinStdlibCommonAttributes)
                .as("the artifact manifest should be present on the runtime classpath")
                .isNotNull();
        assertThat(kotlinStdlibCommonAttributes.getValue("Implementation-Vendor")).isEqualTo("JetBrains");
        assertThat(kotlinStdlibCommonAttributes.getValue("Kotlin-Runtime-Component")).isEqualTo("Main");
        assertThat(kotlinStdlibCommonAttributes.getValue("Kotlin-Version"))
                .as("the manifest should expose the Kotlin language line without pinning this test to a patch release")
                .isNotBlank();
    }

    private static byte[] readRequiredResource(String resourceName) throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(resourceName)) {
            assertThat(inputStream)
                    .as("expected resource %s to be available from the published artifact", resourceName)
                    .isNotNull();
            return inputStream.readAllBytes();
        }
    }
}
