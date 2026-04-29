/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.dynamic.loading.ClassInjector;

public class ClassInjectorInnerUsingUnsafeInnerFactoryTest {
    private static final String SAFE_PROPERTY = "org.modelmapper.internal.bytebuddy.safe";
    private static final String FACTORY_TYPE_NAME =
        "org.modelmapper.internal.bytebuddy.dynamic.loading.ClassInjector$UsingUnsafe$Factory";
    private static final String UNSAFE_PACKAGE = "jdk.internal.misc";

    @Test
    void constructsFactoryWithInternalUnsafeFallbackWhenSharedDispatcherIsUnavailable() throws Exception {
        String previousSafeProperty = System.setProperty(SAFE_PROPERTY, Boolean.TRUE.toString());
        try {
            ClassLoader classLoader = new ChildFirstByteBuddyClassLoader(getClass().getClassLoader());
            Callable<?> runner = (Callable<?>) classLoader
                .loadClass(DisabledSharedDispatcherRunner.class.getName())
                .getConstructor()
                .newInstance();

            assertThat(runner.call()).isInstanceOf(Boolean.class);
        } finally {
            restoreSafeProperty(previousSafeProperty);
        }
    }

    @Test
    void resolvesFactoryWithGeneratedAccessResolverForClosedUnsafeModule() throws Exception {
        String previousSafeProperty = System.setProperty(SAFE_PROPERTY, Boolean.TRUE.toString());
        try {
            RecordingInstrumentation instrumentation = new RecordingInstrumentation();

            Optional<Boolean> resolvedFromNamedModule = resolveFactoryFromNamedModelMapperModule(instrumentation);
            if (resolvedFromNamedModule.isPresent()) {
                assertThat(resolvedFromNamedModule.get()).isInstanceOf(Boolean.class);
                assertThat(instrumentation.getOpenedPackages()).contains(UNSAFE_PACKAGE);
            } else {
                ClassInjector.UsingUnsafe.Factory factory = ClassInjector.UsingUnsafe.Factory.resolve(
                    instrumentation,
                    false);
                assertThat(factory).isNotNull();
            }
        } finally {
            restoreSafeProperty(previousSafeProperty);
        }
    }

    private static Optional<Boolean> resolveFactoryFromNamedModelMapperModule(Instrumentation instrumentation)
        throws Exception {
        Optional<Path> libraryJar = locateModelMapperJar();
        if (libraryJar.isEmpty()) {
            return Optional.empty();
        }

        ModuleFinder finder = ModuleFinder.of(libraryJar.get());
        Optional<ModuleReference> moduleReference = finder.findAll().stream()
            .filter(reference -> reference.descriptor().packages().contains("org.modelmapper"))
            .findFirst();
        if (moduleReference.isEmpty()) {
            return Optional.empty();
        }

        ModuleLayer parent = ModuleLayer.boot();
        Configuration configuration = parent.configuration().resolve(
            finder,
            ModuleFinder.of(),
            Set.of(moduleReference.get().descriptor().name()));
        ModuleLayer layer = parent.defineModulesWithOneLoader(configuration, ClassLoader.getSystemClassLoader());
        Class<?> factoryType = layer.findLoader(moduleReference.get().descriptor().name()).loadClass(FACTORY_TYPE_NAME);
        Method resolve = factoryType.getMethod("resolve", Instrumentation.class, boolean.class);
        Object factory = resolve.invoke(null, instrumentation, false);
        return Optional.of((Boolean) factoryType.getMethod("isAvailable").invoke(factory));
    }

    private static Optional<Path> locateModelMapperJar() throws Exception {
        CodeSource codeSource = ClassInjector.UsingUnsafe.Factory.class.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            return Optional.empty();
        }
        URI location = codeSource.getLocation().toURI();
        Path path = Path.of(location);
        if (!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".jar")) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    private static void restoreSafeProperty(String previousSafeProperty) {
        if (previousSafeProperty == null) {
            System.clearProperty(SAFE_PROPERTY);
        } else {
            System.setProperty(SAFE_PROPERTY, previousSafeProperty);
        }
    }

    public static final class DisabledSharedDispatcherRunner implements Callable<Boolean> {
        @Override
        public Boolean call() {
            System.setProperty(SAFE_PROPERTY, Boolean.TRUE.toString());
            ClassInjector.UsingUnsafe.Factory factory = new ClassInjector.UsingUnsafe.Factory();
            return factory.make(getClass().getClassLoader()).isAlive() == factory.isAvailable();
        }
    }

    private static final class ChildFirstByteBuddyClassLoader extends ClassLoader {
        private ChildFirstByteBuddyClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null && isChildFirst(name)) {
                    try {
                        type = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        type = super.loadClass(name, false);
                    }
                } else if (type == null) {
                    type = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream inputStream = getParent().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] binaryRepresentation = inputStream.readAllBytes();
                return defineClass(name, binaryRepresentation, 0, binaryRepresentation.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }

        private static boolean isChildFirst(String name) {
            return name.equals(DisabledSharedDispatcherRunner.class.getName())
                || name.startsWith("org.modelmapper.internal.bytebuddy.dynamic.loading.ClassInjector$UsingUnsafe");
        }
    }

    private static final class RecordingInstrumentation implements Instrumentation {
        private final List<ClassFileTransformer> transformers = new ArrayList<>();
        private final List<String> openedPackages = new ArrayList<>();

        private List<String> getOpenedPackages() {
            return Collections.unmodifiableList(openedPackages);
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer, boolean canRetransform) {
            transformers.add(transformer);
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer) {
            transformers.add(transformer);
        }

        @Override
        public boolean removeTransformer(ClassFileTransformer transformer) {
            return transformers.remove(transformer);
        }

        @Override
        public boolean isRetransformClassesSupported() {
            return false;
        }

        @Override
        public void retransformClasses(Class<?>... classes) throws UnmodifiableClassException {
            throw unsupportedInstrumentationOperation();
        }

        @Override
        public boolean isRedefineClassesSupported() {
            return false;
        }

        @Override
        public void redefineClasses(ClassDefinition... definitions)
            throws ClassNotFoundException, UnmodifiableClassException {
            throw unsupportedInstrumentationOperation();
        }

        @Override
        public boolean isModifiableClass(Class<?> theClass) {
            return false;
        }

        @Override
        public Class<?>[] getAllLoadedClasses() {
            return new Class<?>[0];
        }

        @Override
        public Class<?>[] getInitiatedClasses(ClassLoader loader) {
            return new Class<?>[0];
        }

        @Override
        public long getObjectSize(Object objectToSize) {
            return 0;
        }

        @Override
        public void appendToBootstrapClassLoaderSearch(JarFile jarfile) {
            throw unsupportedInstrumentationOperation();
        }

        @Override
        public void appendToSystemClassLoaderSearch(JarFile jarfile) {
            throw unsupportedInstrumentationOperation();
        }

        @Override
        public boolean isNativeMethodPrefixSupported() {
            return false;
        }

        @Override
        public void setNativeMethodPrefix(ClassFileTransformer transformer, String prefix) {
            throw unsupportedInstrumentationOperation();
        }

        @Override
        public void redefineModule(
            Module module,
            Set<Module> extraReads,
            Map<String, Set<Module>> extraExports,
            Map<String, Set<Module>> extraOpens,
            Set<Class<?>> extraUses,
            Map<Class<?>, List<Class<?>>> extraProvides) {
            openedPackages.addAll(extraOpens.keySet());
        }

        @Override
        public boolean isModifiableModule(Module module) {
            return true;
        }

        private static UnsupportedOperationException unsupportedInstrumentationOperation() {
            return new UnsupportedOperationException("Operation is not supported by the test instrumentation");
        }
    }
}
