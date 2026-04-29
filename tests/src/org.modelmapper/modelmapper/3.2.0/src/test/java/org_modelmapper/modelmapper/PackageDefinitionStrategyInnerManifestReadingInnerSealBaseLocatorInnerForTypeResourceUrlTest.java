/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import java.net.URL;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.dynamic.loading.PackageDefinitionStrategy;

public class PackageDefinitionStrategyInnerManifestReadingInnerSealBaseLocatorInnerForTypeResourceUrlTest {

    private static final String TYPE_NAME = "org.example.GeneratedType";
    private static final String TYPE_RESOURCE_NAME = "org/example/GeneratedType.class";

    @Test
    void resolvesSealBaseFromTypeClassResourceUrl() throws MalformedURLException {
        URL classResourceUrl = new URL("file:/application/classes/org/example/GeneratedType.class");
        TypeResourceClassLoader classLoader = new TypeResourceClassLoader(classResourceUrl);

        URL sealBase = new PackageDefinitionStrategy.ManifestReading.SealBaseLocator.ForTypeResourceUrl(
            (loader, typeName) -> {
                throw new AssertionError("Fallback should not be used when the type resource exists");
            })
            .findSealBase(classLoader, TYPE_NAME);

        assertThat(classLoader.getRequestedResource()).isEqualTo(TYPE_RESOURCE_NAME);
        assertThat(sealBase).isSameAs(classResourceUrl);
    }

    @Test
    void delegatesToFallbackWhenTypeResourceIsMissing() throws MalformedURLException {
        URL fallbackUrl = new URL("file:/application/fallback.jar");
        TypeResourceClassLoader classLoader = new TypeResourceClassLoader(null);

        URL sealBase = new PackageDefinitionStrategy.ManifestReading.SealBaseLocator.ForTypeResourceUrl(
            (loader, typeName) -> {
                assertThat(loader).isSameAs(classLoader);
                assertThat(typeName).isEqualTo(TYPE_NAME);
                return fallbackUrl;
            })
            .findSealBase(classLoader, TYPE_NAME);

        assertThat(classLoader.getRequestedResource()).isEqualTo(TYPE_RESOURCE_NAME);
        assertThat(sealBase).isSameAs(fallbackUrl);
    }

    private static final class TypeResourceClassLoader extends ClassLoader {
        private final URL classResourceUrl;
        private String requestedResource;

        TypeResourceClassLoader(URL classResourceUrl) {
            super(
                PackageDefinitionStrategyInnerManifestReadingInnerSealBaseLocatorInnerForTypeResourceUrlTest.class
                    .getClassLoader());
            this.classResourceUrl = classResourceUrl;
        }

        @Override
        public URL getResource(String name) {
            requestedResource = name;
            if (TYPE_RESOURCE_NAME.equals(name)) {
                return classResourceUrl;
            }
            return null;
        }

        String getRequestedResource() {
            return requestedResource;
        }
    }

}
