/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.aspectj.weaver.loadtime.DefaultWeavingContext;
import org.junit.jupiter.api.Test;

public class DefaultWeavingContextTest {
    private static final String AOP_RESOURCE = "META-INF/aop.xml";
    private static final String LOCAL_CLASS_NAME = "com.example.LocalType";
    private static final String LOCAL_CLASS_RESOURCE = "com/example/LocalType.class";

    @Test
    void getResourcesDelegatesToConfiguredClassLoader() throws IOException {
        URL firstDefinition = new URL("file:/aspectj/test/first-aop.xml");
        URL secondDefinition = new URL("file:/aspectj/test/second-aop.xml");
        RecordingResourceClassLoader loader = new RecordingResourceClassLoader(
                null,
                Map.of(),
                Map.of(AOP_RESOURCE, List.of(firstDefinition, secondDefinition)));
        DefaultWeavingContext context = new DefaultWeavingContext(loader);

        List<URL> definitions = Collections.list(context.getResources(AOP_RESOURCE));

        assertThat(definitions).containsExactly(firstDefinition, secondDefinition);
        assertThat(loader.requestedResourceEnumerations()).containsExactly(AOP_RESOURCE);
    }

    @Test
    void isLocallyDefinedChecksLocalLoaderAndParentLoader() throws IOException {
        URL localClass = new URL("file:/aspectj/test/child/com/example/LocalType.class");
        URL parentClass = new URL("file:/aspectj/test/parent/com/example/LocalType.class");
        RecordingResourceClassLoader parentLoader = new RecordingResourceClassLoader(
                null,
                Map.of(LOCAL_CLASS_RESOURCE, parentClass),
                Map.of());
        RecordingResourceClassLoader childLoader = new RecordingResourceClassLoader(
                parentLoader,
                Map.of(LOCAL_CLASS_RESOURCE, localClass),
                Map.of());
        DefaultWeavingContext context = new DefaultWeavingContext(childLoader);

        boolean locallyDefined = context.isLocallyDefined(LOCAL_CLASS_NAME);

        assertThat(locallyDefined).isTrue();
        assertThat(childLoader.requestedSingleResources()).containsExactly(LOCAL_CLASS_RESOURCE);
        assertThat(parentLoader.requestedSingleResources()).containsExactly(LOCAL_CLASS_RESOURCE);
    }

    @Test
    void isLocallyDefinedRejectsParentDefinedClass() throws IOException {
        URL sharedClass = new URL("file:/aspectj/test/shared/com/example/LocalType.class");
        RecordingResourceClassLoader parentLoader = new RecordingResourceClassLoader(
                null,
                Map.of(LOCAL_CLASS_RESOURCE, sharedClass),
                Map.of());
        RecordingResourceClassLoader childLoader = new RecordingResourceClassLoader(
                parentLoader,
                Map.of(LOCAL_CLASS_RESOURCE, sharedClass),
                Map.of());
        DefaultWeavingContext context = new DefaultWeavingContext(childLoader);

        boolean locallyDefined = context.isLocallyDefined(LOCAL_CLASS_NAME);

        assertThat(locallyDefined).isFalse();
        assertThat(childLoader.requestedSingleResources()).containsExactly(LOCAL_CLASS_RESOURCE);
        assertThat(parentLoader.requestedSingleResources()).containsExactly(LOCAL_CLASS_RESOURCE);
    }

    @Test
    void isLocallyDefinedReturnsFalseWhenClassResourceIsMissing() {
        RecordingResourceClassLoader loader = new RecordingResourceClassLoader(null, Map.of(), Map.of());
        DefaultWeavingContext context = new DefaultWeavingContext(loader);

        boolean locallyDefined = context.isLocallyDefined(LOCAL_CLASS_NAME);

        assertThat(locallyDefined).isFalse();
        assertThat(loader.requestedSingleResources()).containsExactly(LOCAL_CLASS_RESOURCE);
    }

    private static class RecordingResourceClassLoader extends ClassLoader {
        private final Map<String, URL> singleResources;
        private final Map<String, List<URL>> resourceEnumerations;
        private final List<String> requestedSingleResources = new ArrayList<>();
        private final List<String> requestedResourceEnumerations = new ArrayList<>();

        RecordingResourceClassLoader(
                ClassLoader parent,
                Map<String, URL> singleResources,
                Map<String, List<URL>> resourceEnumerations) {
            super(parent);
            this.singleResources = singleResources;
            this.resourceEnumerations = resourceEnumerations;
        }

        @Override
        public URL getResource(String name) {
            requestedSingleResources.add(name);
            if (singleResources.containsKey(name)) {
                return singleResources.get(name);
            }
            return super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            requestedResourceEnumerations.add(name);
            if (resourceEnumerations.containsKey(name)) {
                return Collections.enumeration(resourceEnumerations.get(name));
            }
            return super.getResources(name);
        }

        private List<String> requestedSingleResources() {
            return requestedSingleResources;
        }

        private List<String> requestedResourceEnumerations() {
            return requestedResourceEnumerations;
        }
    }
}
