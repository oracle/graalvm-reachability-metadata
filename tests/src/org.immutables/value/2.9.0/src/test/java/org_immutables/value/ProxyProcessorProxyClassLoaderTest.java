/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_immutables.value;

import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Supplier;

import org.immutables.processor.ProxyProcessor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProxyProcessorProxyClassLoaderTest {

    private static final String OSGI_ARCH_PROPERTY = "osgi.arch";
    private static final String SUPPLIER_SERVICE_RESOURCE = "META-INF/services/java.util.function.Supplier";
    private static final String ECLIPSE_COMPILER_ELEMENT_IMPL = "org.eclipse.jdt.internal.compiler.apt.model.ElementImpl";
    private static final String ECLIPSE_PACKAGE_PREFIX = "org.eclipse.";
    private static final String IMMUTABLES_PACKAGE_PREFIX = "org.immutables.";

    @Test
    void proxyProcessorLoadsEclipseTypesFromTheContextClassLoader() throws Exception {
        String previousOsgiArch = System.getProperty(OSGI_ARCH_PROPERTY);
        Thread currentThread = Thread.currentThread();
        ClassLoader previousContextClassLoader = currentThread.getContextClassLoader();

        URL[] urls = {
                codeSourceUrl(ProxyProcessorProxyClassLoaderTest.class),
                resourceRootUrl(SUPPLIER_SERVICE_RESOURCE),
                codeSourceUrl(ProxyProcessor.class)
        };

        try (ChildFirstClassLoader isolatedClassLoader = new ChildFirstClassLoader(urls, previousContextClassLoader)) {
            System.setProperty(OSGI_ARCH_PROPERTY, "test-arch");
            currentThread.setContextClassLoader(previousContextClassLoader);

            Object supportedAnnotationTypes = ServiceLoader.load(Supplier.class, isolatedClassLoader)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected an isolated ProxyProcessor supplier"))
                    .get();

            assertThat(supportedAnnotationTypes).isInstanceOf(Set.class);
            assertThat((Set<?>) supportedAnnotationTypes)
                    .isNotEmpty()
                    .allSatisfy(annotationType -> assertThat(annotationType).isInstanceOf(String.class).isNotEqualTo(""));

            ClassLoader proxyClassLoader = supportedAnnotationTypes.getClass().getClassLoader();
            assertThat(proxyClassLoader).isNotNull();
            Class<?> eclipseCompilerType = proxyClassLoader.loadClass(ECLIPSE_COMPILER_ELEMENT_IMPL);
            assertThat(eclipseCompilerType.getName()).isEqualTo(ECLIPSE_COMPILER_ELEMENT_IMPL);
        } finally {
            currentThread.setContextClassLoader(previousContextClassLoader);
            restoreSystemProperty(previousOsgiArch);
        }
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    private static URL resourceRootUrl(String resourceName) throws Exception {
        URL resource = ProxyProcessorProxyClassLoaderTest.class.getClassLoader().getResource(resourceName);
        assertThat(resource).isNotNull();
        String resourceUrl = resource.toExternalForm();
        assertThat(resourceUrl).endsWith(resourceName);
        return java.net.URI.create(resourceUrl.substring(0, resourceUrl.length() - resourceName.length())).toURL();
    }

    private static void restoreSystemProperty(String previousValue) {
        if (previousValue == null) {
            System.clearProperty(OSGI_ARCH_PROPERTY);
        } else {
            System.setProperty(OSGI_ARCH_PROPERTY, previousValue);
        }
    }

    private static final class ChildFirstClassLoader extends URLClassLoader {

        private ChildFirstClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    if (isChildFirst(name)) {
                        try {
                            loadedClass = findClass(name);
                        } catch (ClassNotFoundException ignored) {
                            loadedClass = super.loadClass(name, false);
                        }
                    } else {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private boolean isChildFirst(String className) {
            return className.startsWith(ECLIPSE_PACKAGE_PREFIX) || className.startsWith(IMMUTABLES_PACKAGE_PREFIX);
        }
    }
}
