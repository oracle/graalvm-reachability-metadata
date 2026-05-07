/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_base;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.TypeConverterLoaderException;
import org.apache.camel.impl.converter.BaseTypeConverterRegistry;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PackageScanFilter;
import org.apache.camel.spi.TypeConverterLoader;
import org.apache.camel.spi.TypeConverterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseTypeConverterRegistryTest {
    @TempDir
    private Path tempDir;

    @Test
    void loadCoreAndFastTypeConvertersDiscoversServiceResourceAndLoadsRegisteredLoader() throws Exception {
        RecordingTypeConverterLoader.reset();
        URL serviceUrl = writeTypeConverterLoaderService();
        ClassLoader controlledClassLoader = new TypeConverterLoaderServiceClassLoader(
                BaseTypeConverterRegistryTest.class.getClassLoader(), serviceUrl);
        PackageScanClassResolver resolver = new SingleClassLoaderResolver(controlledClassLoader);
        DefaultTypeConverter registry = new DefaultTypeConverter(resolver, new RecordingInjector(), false, false);

        registry.loadCoreAndFastTypeConverters();

        assertThat(RecordingTypeConverterLoader.loadCount()).isEqualTo(1);
    }

    private URL writeTypeConverterLoaderService() throws IOException {
        Path serviceFile = tempDir.resolve("type-converter-loader-service");
        Files.writeString(serviceFile, RecordingTypeConverterLoader.class.getName() + System.lineSeparator());
        return serviceFile.toUri().toURL();
    }

    public static final class RecordingTypeConverterLoader implements TypeConverterLoader {
        private static final AtomicInteger LOADS = new AtomicInteger();

        public static void reset() {
            LOADS.set(0);
        }

        public static int loadCount() {
            return LOADS.get();
        }

        @Override
        public void load(TypeConverterRegistry registry) throws TypeConverterLoaderException {
            LOADS.incrementAndGet();
        }
    }

    private static final class TypeConverterLoaderServiceClassLoader extends ClassLoader {
        private final URL serviceUrl;

        private TypeConverterLoaderServiceClassLoader(ClassLoader parent, URL serviceUrl) {
            super(parent);
            this.serviceUrl = serviceUrl;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (RecordingTypeConverterLoader.class.getName().equals(name)) {
                return RecordingTypeConverterLoader.class;
            }
            return super.loadClass(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (BaseTypeConverterRegistry.META_INF_SERVICES_TYPE_CONVERTER_LOADER.equals(name)) {
                return Collections.enumeration(Set.of(serviceUrl));
            }
            return Collections.emptyEnumeration();
        }
    }

    private static final class SingleClassLoaderResolver implements PackageScanClassResolver {
        private final Set<ClassLoader> classLoaders = new LinkedHashSet<>();

        private SingleClassLoaderResolver(ClassLoader classLoader) {
            classLoaders.add(classLoader);
        }

        @Override
        public Set<ClassLoader> getClassLoaders() {
            return classLoaders;
        }

        @Override
        public void addClassLoader(ClassLoader classLoader) {
            classLoaders.add(classLoader);
        }

        @Override
        public Set<Class<?>> findAnnotated(Class<? extends Annotation> annotation, String... packageNames) {
            return Collections.emptySet();
        }

        @Override
        public Set<Class<?>> findAnnotated(Set<Class<? extends Annotation>> annotations, String... packageNames) {
            return Collections.emptySet();
        }

        @Override
        public Set<Class<?>> findImplementations(Class<?> parent, String... packageNames) {
            return Collections.emptySet();
        }

        @Override
        public Set<Class<?>> findByFilter(PackageScanFilter filter, String... packageNames) {
            return Collections.emptySet();
        }

        @Override
        public void addFilter(PackageScanFilter filter) {
        }

        @Override
        public void removeFilter(PackageScanFilter filter) {
        }

        @Override
        public void setAcceptableSchemes(String schemes) {
        }

        @Override
        public void clearCache() {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    }

    private static final class RecordingInjector implements Injector {
        @Override
        public <T> T newInstance(Class<T> type) {
            if (RecordingTypeConverterLoader.class.equals(type)) {
                return type.cast(new RecordingTypeConverterLoader());
            }
            throw new IllegalArgumentException("Unsupported injected type: " + type.getName());
        }

        @Override
        public <T> T newInstance(Class<T> type, String factoryMethod) {
            return newInstance(type);
        }

        @Override
        public <T> T newInstance(Class<T> type, Class<?> factoryClass, String factoryMethod) {
            return newInstance(type);
        }

        @Override
        public <T> T newInstance(Class<T> type, boolean postProcessBean) {
            return newInstance(type);
        }

        @Override
        public boolean supportsAutoWiring() {
            return false;
        }
    }
}
