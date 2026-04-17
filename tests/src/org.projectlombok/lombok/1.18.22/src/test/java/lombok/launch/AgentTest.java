/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package lombok.launch;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;

class AgentTest {
    @Test
    void premainInvokesShadowLoadedAgentLauncher() throws Throwable {
        StubInstrumentation instrumentation = new StubInstrumentation();

        Agent.premain("test-args", instrumentation);

        assertThat(instrumentation.addTransformerCalls).isEqualTo(1);
        assertThat(instrumentation.retransformCalls).isEqualTo(0);
        assertThat(instrumentation.lastCanRetransform).isTrue();
        assertThat(instrumentation.lastTransformer).isNotNull();
    }

    @Test
    void agentmainInvokesShadowLoadedAgentLauncherAndRequestsReload() throws Throwable {
        StubInstrumentation instrumentation = new StubInstrumentation();

        Agent.agentmain("test-args", instrumentation);

        assertThat(instrumentation.addTransformerCalls).isEqualTo(1);
        assertThat(instrumentation.getAllLoadedClassesCalls).isEqualTo(1);
        assertThat(instrumentation.retransformCalls).isEqualTo(0);
        assertThat(instrumentation.lastCanRetransform).isTrue();
        assertThat(instrumentation.lastTransformer).isNotNull();
    }

    private static final class StubInstrumentation implements Instrumentation {
        private int addTransformerCalls;
        private int getAllLoadedClassesCalls;
        private int retransformCalls;
        private boolean lastCanRetransform;
        private ClassFileTransformer lastTransformer;

        @Override
        public void addTransformer(ClassFileTransformer transformer, boolean canRetransform) {
            this.addTransformerCalls++;
            this.lastTransformer = transformer;
            this.lastCanRetransform = canRetransform;
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer) {
            this.addTransformerCalls++;
            this.lastTransformer = transformer;
            this.lastCanRetransform = false;
        }

        @Override
        public boolean removeTransformer(ClassFileTransformer transformer) {
            return transformer == lastTransformer;
        }

        @Override
        public boolean isRetransformClassesSupported() {
            return true;
        }

        @Override
        public void retransformClasses(Class<?>... classes) throws UnmodifiableClassException {
            this.retransformCalls++;
        }

        @Override
        public boolean isRedefineClassesSupported() {
            return false;
        }

        @Override
        public void redefineClasses(ClassDefinition... definitions)
                throws ClassNotFoundException, UnmodifiableClassException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isModifiableClass(Class<?> theClass) {
            return false;
        }

        @Override
        public Class<?>[] getAllLoadedClasses() {
            this.getAllLoadedClassesCalls++;
            return new Class<?>[0];
        }

        @Override
        public Class<?>[] getInitiatedClasses(ClassLoader loader) {
            return new Class<?>[0];
        }

        @Override
        public long getObjectSize(Object objectToSize) {
            return 0L;
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
            return false;
        }

        @Override
        public void setNativeMethodPrefix(ClassFileTransformer transformer, String prefix) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void redefineModule(
                Module module,
                Set<Module> extraReads,
                Map<String, Set<Module>> extraExports,
                Map<String, Set<Module>> extraOpens,
                Set<Class<?>> extraUses,
                Map<Class<?>, List<Class<?>>> extraProvides) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isModifiableModule(Module module) {
            return false;
        }
    }
}
