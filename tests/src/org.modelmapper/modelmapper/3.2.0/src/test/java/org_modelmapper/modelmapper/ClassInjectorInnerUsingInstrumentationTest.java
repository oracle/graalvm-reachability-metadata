/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.modelmapper.internal.bytebuddy.dynamic.loading.ClassInjector;

public class ClassInjectorInnerUsingInstrumentationTest {

    @TempDir
    File temporaryFolder;

    @Test
    void resolvesInjectedTypeFromSystemClassLoader() {
        Map<String, byte[]> types = Collections.singletonMap(SystemVisibleType.class.getName(), new byte[0]);
        ClassInjector classInjector = ClassInjector.UsingInstrumentation.of(
            temporaryFolder,
            ClassInjector.UsingInstrumentation.Target.SYSTEM,
            new NoOpInstrumentation());

        Map<String, Class<?>> injectedTypes = classInjector.injectRaw(types);

        assertThat(injectedTypes).containsEntry(SystemVisibleType.class.getName(), SystemVisibleType.class);
    }

    public static final class SystemVisibleType {
    }

    private static final class NoOpInstrumentation implements Instrumentation {
        @Override
        public void addTransformer(ClassFileTransformer transformer, boolean canRetransform) {
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer) {
        }

        @Override
        public boolean removeTransformer(ClassFileTransformer transformer) {
            return false;
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
        }

        @Override
        public void appendToSystemClassLoaderSearch(JarFile jarfile) {
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
            throw unsupportedInstrumentationOperation();
        }

        @Override
        public boolean isModifiableModule(Module module) {
            return false;
        }

        private static UnsupportedOperationException unsupportedInstrumentationOperation() {
            return new UnsupportedOperationException("Operation is not supported by the test instrumentation");
        }
    }
}
