/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.dynamic.loading.PackageDefinitionStrategy;

public class PackageDefinitionStrategyInnerManifestReadingTest {

    private static final String PACKAGE_NAME = "org.example";
    private static final String TYPE_NAME = "org.example.GeneratedType";

    @Test
    void readsPackageDefinitionFromManifestResource() throws MalformedURLException {
        ManifestResourceClassLoader classLoader = new ManifestResourceClassLoader("""
            Manifest-Version: 1.0
            Specification-Title: main specification
            Specification-Version: main specification version
            Specification-Vendor: main specification vendor
            Implementation-Title: main implementation
            Implementation-Version: main implementation version
            Implementation-Vendor: main implementation vendor
            Sealed: false

            Name: org/example/
            Specification-Title: package specification
            Implementation-Version: package implementation version
            Sealed: true

            """);
        URL sealBase = new URL("file:/org/example/generated.jar");

        PackageDefinitionStrategy.Definition definition = new PackageDefinitionStrategy.ManifestReading(
            (loader, typeName) -> sealBase)
            .define(classLoader, PACKAGE_NAME, TYPE_NAME);

        assertThat(classLoader.getRequestedResource()).isEqualTo(JarFile.MANIFEST_NAME);
        assertThat(definition.isDefined()).isTrue();
        assertThat(definition.getSpecificationTitle()).isEqualTo("package specification");
        assertThat(definition.getSpecificationVersion()).isEqualTo("main specification version");
        assertThat(definition.getSpecificationVendor()).isEqualTo("main specification vendor");
        assertThat(definition.getImplementationTitle()).isEqualTo("main implementation");
        assertThat(definition.getImplementationVersion()).isEqualTo("package implementation version");
        assertThat(definition.getImplementationVendor()).isEqualTo("main implementation vendor");
        assertThat(definition.getSealBase()).isSameAs(sealBase);
    }

    @Test
    void returnsEmptyPackageDefinitionWhenManifestResourceIsMissing() {
        ManifestResourceClassLoader classLoader = new ManifestResourceClassLoader(null);

        PackageDefinitionStrategy.Definition definition = new PackageDefinitionStrategy.ManifestReading()
            .define(classLoader, PACKAGE_NAME, TYPE_NAME);

        assertThat(classLoader.getRequestedResource()).isEqualTo(JarFile.MANIFEST_NAME);
        assertThat(definition.isDefined()).isTrue();
        assertThat(definition.getSpecificationTitle()).isNull();
        assertThat(definition.getSpecificationVersion()).isNull();
        assertThat(definition.getSpecificationVendor()).isNull();
        assertThat(definition.getImplementationTitle()).isNull();
        assertThat(definition.getImplementationVersion()).isNull();
        assertThat(definition.getImplementationVendor()).isNull();
        assertThat(definition.getSealBase()).isNull();
    }

    private static final class ManifestResourceClassLoader extends ClassLoader {
        private final String manifestContent;
        private String requestedResource;

        ManifestResourceClassLoader(String manifestContent) {
            super(PackageDefinitionStrategyInnerManifestReadingTest.class.getClassLoader());
            this.manifestContent = manifestContent;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            requestedResource = name;
            if (JarFile.MANIFEST_NAME.equals(name) && manifestContent != null) {
                return new ByteArrayInputStream(manifestContent.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        }

        String getRequestedResource() {
            return requestedResource;
        }
    }

}
