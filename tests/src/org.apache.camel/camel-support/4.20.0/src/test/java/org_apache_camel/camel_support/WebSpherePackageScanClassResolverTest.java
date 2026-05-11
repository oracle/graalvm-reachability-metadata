/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_support;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import org.apache.camel.support.scan.WebSpherePackageScanClassResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WebSpherePackageScanClassResolverTest {
    @Test
    void findByFilterUsesConfiguredResourceWhenPackageLookupIsEmpty() {
        String fallbackResourcePath = "META-INF/services/org/apache/camel/websphere-package-scan-fallback";
        RecordingClassLoader recordingClassLoader = new RecordingClassLoader();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(recordingClassLoader);
        try {
            WebSpherePackageScanClassResolver resolver = new WebSpherePackageScanClassResolver(fallbackResourcePath);

            Set<Class<?>> matches = resolver.findByFilter(type -> false, "websphere.missing.package");

            assertThat(matches).isEmpty();
            assertThat(recordingClassLoader.requestedNames()).containsSubsequence(
                    "websphere/missing/package/",
                    fallbackResourcePath);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final List<String> requestedNames = new ArrayList<>();

        RecordingClassLoader() {
            super(null);
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            requestedNames.add(name);
            return Collections.emptyEnumeration();
        }

        List<String> requestedNames() {
            return requestedNames;
        }
    }
}
