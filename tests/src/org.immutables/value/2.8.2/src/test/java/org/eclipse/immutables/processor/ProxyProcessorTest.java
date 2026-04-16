/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.eclipse.immutables.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ServiceLoader;
import javax.annotation.processing.Processor;
import org.junit.jupiter.api.Test;

class ProxyProcessorTest {
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

            final ServiceLoader<Processor> serviceLoader = ServiceLoader.load(Processor.class, proxyProcessorLoader);
            final Processor processor = serviceLoader.iterator().next();

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
}
