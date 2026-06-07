/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.utility.JavaModule;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaModuleInnerDispatcherInnerEnabledTest {
    @Test
    void readsModulePropertiesAndResourcesThroughPublicWrapper() throws Exception {
        assertThat(JavaModule.isSupported()).isTrue();

        Class<?> testType = JavaModuleInnerDispatcherInnerEnabledTest.class;
        JavaModule testModule = JavaModule.ofType(testType);
        JavaModule baseModule = JavaModule.ofType(String.class);

        assertThat(testModule.unwrap()).isSameAs(testType.getModule());
        assertThat(JavaModule.of(testModule.unwrap())).isEqualTo(testModule);
        assertThat(testModule.isNamed()).isFalse();
        assertThat(baseModule.isNamed()).isTrue();
        assertThat(testModule.getActualName()).isNull();
        assertThat(baseModule.getActualName()).isEqualTo("java.base");
        assertThat(testModule.getClassLoader()).isSameAs(testType.getClassLoader());
        assertThat(testModule.canRead(baseModule)).isTrue();

        try (InputStream resource = testModule.getResourceAsStream(
                "net_bytebuddy/byte_buddy/JavaModuleInnerDispatcherInnerEnabledTest.class")) {
            assertThat(resource).isNotNull();
            assertThat(resource.read()).isNotEqualTo(-1);
        }
    }

    @Test
    void addsReadEdgeThroughInstrumentationApi() {
        JavaModule sourceModule = JavaModule.ofType(
                JavaModuleInnerDispatcherInnerEnabledTest.class);
        JavaModule targetModule = JavaModule.ofType(String.class);
        RecordingInstrumentation instrumentation = new RecordingInstrumentation();

        sourceModule.addReads(instrumentation, targetModule);

        assertThat(instrumentation.modifiableModule).isSameAs(sourceModule.unwrap());
        assertThat(instrumentation.redefinedModule).isSameAs(sourceModule.unwrap());
        assertThat(instrumentation.extraReads).hasSize(1);
        assertThat(instrumentation.extraReads.iterator().next()).isSameAs(targetModule.unwrap());
        assertThat(instrumentation.extraExports).isEmpty();
        assertThat(instrumentation.extraOpens).isEmpty();
        assertThat(instrumentation.extraUses).isEmpty();
        assertThat(instrumentation.extraProvides).isEmpty();
    }

    private static class RecordingInstrumentation implements Instrumentation {
        Object modifiableModule;
        Object redefinedModule;
        Set<?> extraReads;
        Map<?, ?> extraExports;
        Map<?, ?> extraOpens;
        Set<?> extraUses;
        Map<?, ?> extraProvides;

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
        public Class[] getAllLoadedClasses() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Class[] getInitiatedClasses(ClassLoader loader) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getObjectSize(Object objectToSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void appendToBootstrapClassLoaderSearch(JarFile jarfile) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void appendToSystemClassLoaderSearch(JarFile jarfile) {
            throw new UnsupportedOperationException();
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
            redefinedModule = module;
            this.extraReads = extraReads;
            this.extraExports = extraExports;
            this.extraOpens = extraOpens;
            this.extraUses = extraUses;
            this.extraProvides = extraProvides;
        }

        @Override
        public boolean isModifiableModule(Module module) {
            modifiableModule = module;
            return true;
        }
    }
}
