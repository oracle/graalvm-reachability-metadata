/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

import org.aspectj.weaver.loadtime.DefaultWeavingContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultWeavingContextTest {
    private static final String LOCAL_ONLY_TYPE = "example.LocalOnlyAspect";
    private static final String SHARED_TYPE = "example.SharedAspect";

    @Test
    void reportsClassAsLocallyDefinedWhenOnlyConfiguredLoaderFindsResource() throws Exception {
        URL localUrl = testResourceUrl("local-only");
        ClassLoader parent = new ResourceClassLoader(null, Collections.emptyMap());
        ClassLoader loader = new ResourceClassLoader(parent, Map.of(resourceName(LOCAL_ONLY_TYPE), localUrl));
        DefaultWeavingContext context = new DefaultWeavingContext(loader);

        boolean locallyDefined = context.isLocallyDefined(LOCAL_ONLY_TYPE);

        assertThat(locallyDefined).isTrue();
    }

    @Test
    void reportsClassAsNotLocallyDefinedWhenParentFindsSameResource() throws Exception {
        URL sharedUrl = testResourceUrl("shared");
        String sharedResource = resourceName(SHARED_TYPE);
        ClassLoader parent = new ResourceClassLoader(null, Map.of(sharedResource, sharedUrl));
        ClassLoader loader = new ResourceClassLoader(parent, Map.of(sharedResource, sharedUrl));
        DefaultWeavingContext context = new DefaultWeavingContext(loader);

        boolean locallyDefined = context.isLocallyDefined(SHARED_TYPE);

        assertThat(locallyDefined).isFalse();
    }

    private static String resourceName(String className) {
        return className.replace('.', '/') + ".class";
    }

    private static URL testResourceUrl(String name) throws MalformedURLException {
        return URI.create("file:/default-weaving-context-test/" + name + ".class").toURL();
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private final Map<String, URL> resources;

        private ResourceClassLoader(ClassLoader parent, Map<String, URL> resources) {
            super(parent);
            this.resources = resources;
        }

        @Override
        public URL getResource(String name) {
            return resources.get(name);
        }
    }
}
