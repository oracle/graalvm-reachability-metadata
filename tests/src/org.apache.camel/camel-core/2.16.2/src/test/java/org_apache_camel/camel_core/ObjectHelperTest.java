/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

public class ObjectHelperTest {
    private static final String MANIFEST_RESOURCE = "META-INF/MANIFEST.MF";
    private static final byte[] RESOURCE_BYTES = "object-helper-resource".getBytes(StandardCharsets.UTF_8);

    @Test
    void loadsClassesWithProvidedClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);

            Class<?> loadedClass = ObjectHelper.loadClass(PublicConstructible.class.getName(),
                    ObjectHelperTest.class.getClassLoader());

            assertThat(loadedClass).isEqualTo(PublicConstructible.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void createsNewInstancesAndChecksPublicConstructors() {
        PublicConstructible direct = ObjectHelper.newInstance(PublicConstructible.class);
        ConstructibleContract cast = ObjectHelper.newInstance(PublicConstructible.class, ConstructibleContract.class);

        assertThat(direct.value()).isEqualTo("created");
        assertThat(cast.value()).isEqualTo("created");
        assertThat(ObjectHelper.hasDefaultPublicNoArgConstructor(PublicConstructible.class)).isTrue();
        assertThat(ObjectHelper.hasDefaultPublicNoArgConstructor(OnlyArgumentConstructor.class)).isFalse();
    }

    @Test
    void findsAnnotatedMethodsThroughClassHierarchy() {
        List<Method> methods = ObjectHelper.findMethodsWithAnnotation(AnnotatedChild.class, Marker.class, true);

        assertThat(methods).extracting(Method::getName).contains("childOperation", "parentOperation");
    }

    @Test
    void invokesMethodAndLooksUpConstantFieldValue() throws Exception {
        Method method = Greeter.class.getMethod("greet", String.class);
        Object result = ObjectHelper.invokeMethod(method, new Greeter(), "Camel");

        assertThat(result).isEqualTo("Hello Camel");
        assertThat(ObjectHelper.lookupConstantFieldValue(Constants.class, "ANSWER")).isEqualTo("forty-two");
        assertThat(ObjectHelper.lookupConstantFieldValue(Constants.class, ",ANSWER")).isEqualTo("forty-two");
    }

    @Test
    void loadsResourceStreamsFromExplicitContextLibraryAndClassLookups() throws Exception {
        try (InputStream explicit = ObjectHelper.loadResourceAsStream("explicit.txt", new ResourceClassLoader())) {
            assertThat(explicit).isNotNull();
        }

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ResourceClassLoader());
            try (InputStream context = ObjectHelper.loadResourceAsStream("context.txt", null)) {
                assertThat(context).isNotNull();
            }

            Thread.currentThread().setContextClassLoader(null);
            try (InputStream library = ObjectHelper.loadResourceAsStream(MANIFEST_RESOURCE, null)) {
                assertThat(library).isNotNull();
            }
            try (InputStream classRelative = ObjectHelper.loadResourceAsStream("/" + MANIFEST_RESOURCE, null)) {
                assertThat(classRelative).isNotNull();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void loadsResourceUrlsFromExplicitContextLibraryAndClassLookups() throws Exception {
        assertThat(ObjectHelper.loadResourceAsURL("explicit.txt", new ResourceClassLoader()))
                .isEqualTo(ResourceClassLoader.EXPLICIT_URL);

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ResourceClassLoader());
            assertThat(ObjectHelper.loadResourceAsURL("context.txt", null))
                    .isEqualTo(ResourceClassLoader.CONTEXT_URL);

            Thread.currentThread().setContextClassLoader(null);
            assertThat(ObjectHelper.loadResourceAsURL(MANIFEST_RESOURCE, null)).isNotNull();
            assertThat(ObjectHelper.loadResourceAsURL("/" + MANIFEST_RESOURCE, null)).isNotNull();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void loadsResourceUrlEnumerationsFromExplicitContextAndLibraryClassLoaders() throws Exception {
        Enumeration<URL> explicit = ObjectHelper.loadResourcesAsURL("explicit-package", new ResourceClassLoader());
        assertThat(Collections.list(explicit)).containsExactly(ResourceClassLoader.EXPLICIT_URL);

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ResourceClassLoader());
            Enumeration<URL> context = ObjectHelper.loadResourcesAsURL("context-package", null);
            assertThat(Collections.list(context)).containsExactly(ResourceClassLoader.CONTEXT_URL);

            Thread.currentThread().setContextClassLoader(null);
            Enumeration<URL> library = ObjectHelper.loadResourcesAsURL(MANIFEST_RESOURCE, null);
            assertThat(Collections.list(library)).isNotEmpty();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    public interface ConstructibleContract {
        String value();
    }

    public static class PublicConstructible implements ConstructibleContract {
        @Override
        public String value() {
            return "created";
        }
    }

    public static class OnlyArgumentConstructor {
        public OnlyArgumentConstructor(String value) {
            assertThat(value).isNotNull();
        }
    }

    public static class Constants {
        public static final String ANSWER = "forty-two";
    }

    public static class Greeter {
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    public @interface Marker {
    }

    @Marker
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Stereotype {
    }

    public static class AnnotatedParent {
        @Marker
        public String parentOperation() {
            return "parent";
        }
    }

    public static class AnnotatedChild extends AnnotatedParent {
        @Stereotype
        public String childOperation() {
            return "child";
        }
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private static final URL EXPLICIT_URL = createUrl("file:/object-helper-explicit-resource");
        private static final URL CONTEXT_URL = createUrl("file:/object-helper-context-resource");

        private ResourceClassLoader() {
            super(null);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if ("explicit.txt".equals(name) || "context.txt".equals(name)) {
                return new ByteArrayInputStream(RESOURCE_BYTES);
            }
            return null;
        }

        @Override
        public URL getResource(String name) {
            if ("explicit.txt".equals(name)) {
                return EXPLICIT_URL;
            }
            if ("context.txt".equals(name)) {
                return CONTEXT_URL;
            }
            return null;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if ("explicit-package".equals(name)) {
                return Collections.enumeration(Arrays.asList(EXPLICIT_URL));
            }
            if ("context-package".equals(name)) {
                return Collections.enumeration(Arrays.asList(CONTEXT_URL));
            }
            return Collections.emptyEnumeration();
        }

        private static URL createUrl(String value) {
            try {
                return new URL(value);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
