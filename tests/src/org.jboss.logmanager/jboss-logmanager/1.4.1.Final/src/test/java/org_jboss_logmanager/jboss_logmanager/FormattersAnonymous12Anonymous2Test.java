/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.net.URL;
import java.security.PrivilegedAction;

import org.jboss.logmanager.formatters.Formatters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FormattersAnonymous12Anonymous2Test {

    @Test
    void resourceActionUsesClassLoaderResourceWhenFrameClassHasAClassLoader() throws Throwable {
        final String resourceName = "org/example/FrameClass.class";
        final URL resourceUrl = URI.create("file:/active-class-loader-resource").toURL();
        final TrackingResourceClassLoader classLoader = new TrackingResourceClassLoader(resourceName, resourceUrl);

        final URL result = newResourceLookupAction(classLoader, resourceName).run();

        assertThat(result).isSameAs(resourceUrl);
        assertThat(classLoader.requestedResource()).isEqualTo(resourceName);
    }

    @Test
    void resourceActionUsesSystemResourceWhenFrameClassIsLoadedByBootstrapLoader() throws Throwable {
        final String resourceName = "logging.properties";
        final URL expected = ClassLoader.getSystemResource(resourceName);

        final URL result = newResourceLookupAction(null, resourceName).run();

        assertThat(result).isEqualTo(expected);
    }

    @SuppressWarnings("unchecked")
    private static PrivilegedAction<URL> newResourceLookupAction(
            final ClassLoader classLoader,
            final String resourceName
    ) throws Throwable {
        final Class<?> enclosingFormatterStepClass = Class.forName(Formatters.class.getName() + "$12");
        final Class<?> resourceActionClass = Class.forName(enclosingFormatterStepClass.getName() + "$2");
        final MethodHandle constructor = MethodHandles.privateLookupIn(resourceActionClass, MethodHandles.lookup())
                .findConstructor(
                        resourceActionClass,
                        MethodType.methodType(
                                void.class,
                                enclosingFormatterStepClass,
                                ClassLoader.class,
                                String.class
                        )
                );
        return (PrivilegedAction<URL>) constructor.invoke(null, classLoader, resourceName);
    }

    private static final class TrackingResourceClassLoader extends ClassLoader {
        private final String expectedResource;
        private final URL resourceUrl;
        private String requestedResource;

        private TrackingResourceClassLoader(final String expectedResource, final URL resourceUrl) {
            super(FormattersAnonymous12Anonymous2Test.class.getClassLoader());
            this.expectedResource = expectedResource;
            this.resourceUrl = resourceUrl;
        }

        String requestedResource() {
            return requestedResource;
        }

        @Override
        public URL getResource(final String name) {
            requestedResource = name;
            if (expectedResource.equals(name)) {
                return resourceUrl;
            }
            return super.getResource(name);
        }
    }
}
