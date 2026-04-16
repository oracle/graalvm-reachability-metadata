/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.eclipse.immutables.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ServiceLoader;
import javax.annotation.processing.Processor;
import org.junit.jupiter.api.Test;

class ProxyProcessor$ProxyClassLoaderTest {
    @Test
    void loadsProxyProcessorThroughClassLoaderDelegateWhenEclipseEnvironmentIsDetected() throws Exception {
        final String previousOsgiArch = System.getProperty("osgi.arch");
        final ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        final URL[] classpathUrls = currentClasspathUrls();

        try (URLClassLoader proxyProcessorLoader = new URLClassLoader(
                classpathUrls,
                ClassLoader.getPlatformClassLoader());
                URLClassLoader eclipseContextLoader = new URLClassLoader(
                        classpathUrls,
                        ClassLoader.getPlatformClassLoader())) {
            System.setProperty("osgi.arch", "test-arch");
            Thread.currentThread().setContextClassLoader(eclipseContextLoader);

            final Processor processor = loadProxyProcessor(proxyProcessorLoader);

            assertThat(processor.getClass().getName()).isEqualTo("org.immutables.processor.ProxyProcessor");
            assertThat(processor.getSupportedAnnotationTypes()).isNotEmpty();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            if (previousOsgiArch == null) {
                System.clearProperty("osgi.arch");
            } else {
                System.setProperty("osgi.arch", previousOsgiArch);
            }
        }
    }

    @Test
    void delegatesOrgEclipseClassLoadingToTheContextClassLoader() throws Exception {
        final String previousOsgiArch = System.getProperty("osgi.arch");
        final ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        final URL[] classpathUrls = currentClasspathUrls();

        try (URLClassLoader proxyProcessorLoader = new URLClassLoader(
                classpathUrls,
                ClassLoader.getPlatformClassLoader());
                URLClassLoader eclipseContextLoader = new URLClassLoader(
                        classpathUrls,
                        ClassLoader.getPlatformClassLoader())) {
            System.setProperty("osgi.arch", "test-arch");
            Thread.currentThread().setContextClassLoader(eclipseContextLoader);

            final Processor processor = loadProxyProcessor(proxyProcessorLoader);
            final Processor delegate = delegateOf(processor);
            final ClassLoader delegateClassLoader = delegate.getClass().getClassLoader();
            final String eclipseTypeName = EclipseContextType.class.getName();
            final Class<?> expectedType = eclipseContextLoader.loadClass(eclipseTypeName);
            final Class<?> delegatedType = delegateClassLoader.loadClass(eclipseTypeName);

            assertThat(delegateClassLoader.getClass().getName())
                    .isEqualTo("org.immutables.processor.ProxyProcessor$ProxyClassLoader");
            assertThat(delegatedType).isSameAs(expectedType);
            assertThat(delegatedType.getClassLoader()).isSameAs(eclipseContextLoader);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            if (previousOsgiArch == null) {
                System.clearProperty("osgi.arch");
            } else {
                System.setProperty("osgi.arch", previousOsgiArch);
            }
        }
    }

    private static Processor loadProxyProcessor(URLClassLoader proxyProcessorLoader) {
        final ServiceLoader<Processor> serviceLoader = ServiceLoader.load(Processor.class, proxyProcessorLoader);
        return serviceLoader.iterator().next();
    }

    private static Processor delegateOf(Processor processor) {
        try {
            final Field delegateField = processor.getClass().getDeclaredField("delegate");
            delegateField.setAccessible(true);
            return (Processor) delegateField.get(processor);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot access proxy delegate", exception);
        }
    }

    private static URL[] currentClasspathUrls() {
        final String[] classpathEntries = System.getProperty("java.class.path").split(File.pathSeparator);
        final URL[] classpathUrls = new URL[classpathEntries.length];
        for (int index = 0; index < classpathEntries.length; index++) {
            final Path classpathEntry = Path.of(classpathEntries[index]);
            try {
                classpathUrls[index] = classpathEntry.toUri().toURL();
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot convert classpath entry to URL: " + classpathEntry, exception);
            }
        }
        return classpathUrls;
    }

    static final class EclipseContextType {
    }
}
