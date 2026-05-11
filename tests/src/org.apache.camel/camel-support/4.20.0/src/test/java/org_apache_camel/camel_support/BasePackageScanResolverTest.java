/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_support;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.apache.camel.support.scan.BasePackageScanResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BasePackageScanResolverTest {
    @Test
    void getResourcesNormalizesPackageNameBeforeDelegatingToClassLoader() throws Exception {
        ExposedPackageScanResolver resolver = new ExposedPackageScanResolver();
        RecordingClassLoader loader = new RecordingClassLoader();

        Enumeration<URL> resources = resolver.getResourcesFor(loader, "org/apache/camel/support/scan");

        assertThat(loader.requestedNames()).containsExactly("org/apache/camel/support/scan/");
        assertThat(resources.hasMoreElements()).isTrue();
        assertThat(resources.nextElement()).isEqualTo(loader.resourceUrl());
        assertThat(resources.hasMoreElements()).isFalse();
    }

    private static final class ExposedPackageScanResolver extends BasePackageScanResolver {
        Enumeration<URL> getResourcesFor(ClassLoader loader, String packageName) throws IOException {
            return getResources(loader, packageName);
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final URL resourceUrl;
        private final List<String> requestedNames = new ArrayList<>();

        RecordingClassLoader() throws Exception {
            super(null);
            this.resourceUrl = URI.create("file:/camel-support/package-scan/").toURL();
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            requestedNames.add(name);
            return Collections.enumeration(List.of(resourceUrl));
        }

        URL resourceUrl() {
            return resourceUrl;
        }

        List<String> requestedNames() {
            return requestedNames;
        }
    }
}
