/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.modelmapper.internal.bytebuddy.agent;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

public final class Installer {
    private static RecordingInstrumentation instrumentation = new RecordingInstrumentation();

    private Installer() {
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }

    public static RecordingInstrumentation resetInstrumentation() {
        instrumentation = new RecordingInstrumentation();
        return instrumentation;
    }

    public static final class RecordingInstrumentation implements Instrumentation {
        private final List<ClassFileTransformer> addedTransformers = new ArrayList<>();

        public List<ClassFileTransformer> getAddedTransformers() {
            return Collections.unmodifiableList(addedTransformers);
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer, boolean canRetransform) {
            addedTransformers.add(transformer);
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer) {
            addedTransformers.add(transformer);
        }

        @Override
        public boolean removeTransformer(ClassFileTransformer transformer) {
            return addedTransformers.remove(transformer);
        }

        @Override
        public boolean isRetransformClassesSupported() {
            return false;
        }

        @Override
        public void retransformClasses(Class<?>... classes) throws UnmodifiableClassException {
            throw new UnsupportedOperationException("Retransformation is not supported by the test instrumentation");
        }

        @Override
        public boolean isRedefineClassesSupported() {
            return false;
        }

        @Override
        public void redefineClasses(ClassDefinition... definitions)
            throws ClassNotFoundException, UnmodifiableClassException {
            throw new UnsupportedOperationException("Redefinition is not supported by the test instrumentation");
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
