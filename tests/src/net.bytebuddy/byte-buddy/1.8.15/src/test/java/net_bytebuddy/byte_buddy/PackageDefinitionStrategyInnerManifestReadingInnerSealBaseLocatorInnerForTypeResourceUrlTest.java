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

public class PackageDefinitionStrategyInnerManifestReadingInnerSealBaseLocatorInnerForTypeResourceUrlTest {
    private static final String MANIFEST_RESOURCE = "/META-INF/MANIFEST.MF";
    private static final String PACKAGE_NAME = "com.acme.sealed";
    private static final String TYPE_NAME = PACKAGE_NAME + ".Sample";
    private static final String TYPE_RESOURCE = "com/acme/sealed/Sample.class";

    @TempDir
    Path temporaryDirectory;

    @Test
    void sealedManifestUsesTypeResourceUrlAsSealBase() throws Exception {
        Path manifestFile = writeManifest();
        Path typeResourceFile = temporaryDirectory.resolve(TYPE_RESOURCE);
        Files.createDirectories(typeResourceFile.getParent());
        Files.write(typeResourceFile, new byte[]{0});
        URL typeResourceUrl = typeResourceFile.toUri().toURL();
        TypeResourceClassLoader classLoader = new TypeResourceClassLoader(manifestFile.toUri().toURL(), typeResourceUrl);
        PackageDefinitionStrategy strategy = new PackageDefinitionStrategy.ManifestReading();

        PackageDefinitionStrategy.Definition definition = strategy.define(classLoader, PACKAGE_NAME, TYPE_NAME);

        assertThat(definition.isDefined()).isTrue();
        assertThat(definition.getSealBase()).isEqualTo(typeResourceUrl);
        assertThat(classLoader.lastTypeResourceName).isEqualTo(TYPE_RESOURCE);
    }

    private Path writeManifest() throws IOException {
        Path manifestFile = temporaryDirectory.resolve("META-INF").resolve("MANIFEST.MF");
        Files.createDirectories(manifestFile.getParent());
        Files.write(manifestFile, "Manifest-Version: 1.0\nSealed: true\n\n".getBytes(StandardCharsets.UTF_8));
        return manifestFile;
    }

    private static final class TypeResourceClassLoader extends ClassLoader {
        private final URL manifestUrl;
        private final URL typeResourceUrl;
        private String lastTypeResourceName;

        private TypeResourceClassLoader(URL manifestUrl, URL typeResourceUrl) {
            super(null);
            this.manifestUrl = manifestUrl;
            this.typeResourceUrl = typeResourceUrl;
        }

        @Override
        protected URL findResource(String name) {
            if (MANIFEST_RESOURCE.equals(name)) {
                return manifestUrl;
            } else if (TYPE_RESOURCE.equals(name)) {
                lastTypeResourceName = name;
                return typeResourceUrl;
            } else {
                return null;
            }
        }
    }
}
