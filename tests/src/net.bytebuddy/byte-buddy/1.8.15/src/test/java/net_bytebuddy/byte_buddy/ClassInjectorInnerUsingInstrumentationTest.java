/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassInjectorInnerUsingInstrumentationTest {
    @Test
    void appendsJarToSystemSearchAndResolvesSystemVisibleType(@TempDir File folder) {
        try {
            DynamicType.Unloaded<?> unloaded = new ByteBuddy(ClassFileVersion.JAVA_V8)
                    .subclass(Object.class)
                    .name(SystemClasspathFixture.class.getName())
                    .make();
            TypeDescription typeDescription = unloaded.getTypeDescription();
            RecordingInstrumentation instrumentation = new RecordingInstrumentation();
            ClassInjector injector = ClassInjector.UsingInstrumentation.of(
                    folder,
                    ClassInjector.UsingInstrumentation.Target.SYSTEM,
                    instrumentation);

            Map<TypeDescription, Class<?>> loaded = injector.inject(
                    Collections.singletonMap(typeDescription, unloaded.getBytes()));

            assertThat(instrumentation.bootstrapJarFile).isNull();
            assertThat(instrumentation.systemJarFile).isNotNull();
            assertThat(instrumentation.systemJarFile.getEntry(typeDescription.getInternalName() + ".class"))
                    .isNotNull();
            assertThat(loaded.get(typeDescription)).isSameAs(SystemClasspathFixture.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class SystemClasspathFixture {
    }

    private static class RecordingInstrumentation implements Instrumentation {
        private JarFile bootstrapJarFile;
        private JarFile systemJarFile;

        @Override
        public void addTransformer(ClassFileTransformer transformer, boolean canRetransform) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeTransformer(ClassFileTransformer transformer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isRetransformClassesSupported() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void retransformClasses(Class<?>... classes) throws UnmodifiableClassException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isRedefineClassesSupported() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void redefineClasses(ClassDefinition... definitions)
                throws ClassNotFoundException, UnmodifiableClassException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isModifiableClass(Class<?> theClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Class<?>[] getAllLoadedClasses() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Class<?>[] getInitiatedClasses(ClassLoader loader) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getObjectSize(Object objectToSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void appendToBootstrapClassLoaderSearch(JarFile jarfile) {
            bootstrapJarFile = jarfile;
        }

        @Override
        public void appendToSystemClassLoaderSearch(JarFile jarfile) {
            systemJarFile = jarfile;
        }

        @Override
        public boolean isNativeMethodPrefixSupported() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setNativeMethodPrefix(ClassFileTransformer transformer, String prefix) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void redefineModule(Module module,
                                   Set<Module> extraReads,
                                   Map<String, Set<Module>> extraExports,
                                   Map<String, Set<Module>> extraOpens,
                                   Set<Class<?>> extraUses,
                                   Map<Class<?>, List<Class<?>>> extraProvides) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isModifiableModule(Module module) {
            throw new UnsupportedOperationException();
        }
    }
}
