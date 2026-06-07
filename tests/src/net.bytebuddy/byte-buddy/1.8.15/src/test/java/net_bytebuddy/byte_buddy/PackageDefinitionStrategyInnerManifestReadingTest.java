/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.loading.PackageDefinitionStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class PackageDefinitionStrategyInnerManifestReadingTest {
    private static final String MANIFEST_RESOURCE = "/META-INF/MANIFEST.MF";
    private static final String PACKAGE_NAME = "com.acme.manifest";
    private static final String TYPE_NAME = PACKAGE_NAME + ".Sample";

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsPackageDefinitionFromClassLoaderManifest() throws Exception {
        Path manifestFile = writeManifest(
                "Manifest-Version: 1.0\n"
                        + "Specification-Title: main-specification-title\n"
                        + "Specification-Version: main-specification-version\n"
                        + "Specification-Vendor: main-specification-vendor\n"
                        + "Implementation-Title: main-implementation-title\n"
                        + "Implementation-Version: main-implementation-version\n"
                        + "Implementation-Vendor: main-implementation-vendor\n"
                        + "Sealed: false\n"
                        + "\n"
                        + "Name: com/acme/manifest/\n"
                        + "Specification-Title: package-specification-title\n"
                        + "Implementation-Version: package-implementation-version\n"
                        + "\n");
        ClassLoader classLoader = new ManifestClassLoader(manifestFile.toUri().toURL());
        PackageDefinitionStrategy strategy = new PackageDefinitionStrategy.ManifestReading(
                PackageDefinitionStrategy.ManifestReading.SealBaseLocator.NonSealing.INSTANCE);

        PackageDefinitionStrategy.Definition definition = strategy.define(classLoader, PACKAGE_NAME, TYPE_NAME);

        assertThat(definition.isDefined()).isTrue();
        assertThat(definition.getSpecificationTitle()).isEqualTo("package-specification-title");
        assertThat(definition.getSpecificationVersion()).isEqualTo("main-specification-version");
        assertThat(definition.getSpecificationVendor()).isEqualTo("main-specification-vendor");
        assertThat(definition.getImplementationTitle()).isEqualTo("main-implementation-title");
        assertThat(definition.getImplementationVersion()).isEqualTo("package-implementation-version");
        assertThat(definition.getImplementationVendor()).isEqualTo("main-implementation-vendor");
        assertThat(definition.getSealBase()).isNull();
    }

    private Path writeManifest(String manifest) throws IOException {
        Path manifestFile = temporaryDirectory.resolve("META-INF").resolve("MANIFEST.MF");
        Files.createDirectories(manifestFile.getParent());
        Files.write(manifestFile, manifest.getBytes(StandardCharsets.UTF_8));
        return manifestFile;
    }

    private static final class ManifestClassLoader extends ClassLoader {
        private final URL manifestUrl;

        private ManifestClassLoader(URL manifestUrl) {
            super(null);
            this.manifestUrl = manifestUrl;
        }

        @Override
        protected URL findResource(String name) {
            return MANIFEST_RESOURCE.equals(name) ? manifestUrl : null;
        }
    }
}
