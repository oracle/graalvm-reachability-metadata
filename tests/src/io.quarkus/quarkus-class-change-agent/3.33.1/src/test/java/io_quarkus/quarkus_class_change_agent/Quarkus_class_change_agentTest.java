/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_class_change_agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.changeagent.ClassChangeAgent;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("class-change-agent-instrumentation")
public class Quarkus_class_change_agentTest {
    @Test
    void constructorCreatesUsableAgentFacade() {
        ClassChangeAgent agent = new ClassChangeAgent();

        assertThat(agent).isNotNull();
    }

    @Test
    void premainStoresTheProvidedInstrumentationInstance() {
        RecordingInstrumentation instrumentation = new RecordingInstrumentation();

        ClassChangeAgent.premain("quarkus-dev-mode", instrumentation);

        assertThat(ClassChangeAgent.getInstrumentation()).isSameAs(instrumentation);
    }

    @Test
    void premainReplacesPreviouslyStoredInstrumentation() {
        RecordingInstrumentation firstInstrumentation = new RecordingInstrumentation();
        RecordingInstrumentation secondInstrumentation = new RecordingInstrumentation();

        ClassChangeAgent.premain("first", firstInstrumentation);
        ClassChangeAgent.premain("second", secondInstrumentation);

        assertThat(ClassChangeAgent.getInstrumentation()).isSameAs(secondInstrumentation);
    }

    @Test
    void premainAcceptsNullInstrumentationHandle() {
        ClassChangeAgent.premain("no-instrumentation", null);

        assertThat(ClassChangeAgent.getInstrumentation()).isNull();
    }

    private static final class RecordingInstrumentation implements Instrumentation {
        @Override
        public void addTransformer(ClassFileTransformer transformer, boolean canRetransform) {
            throw new UnsupportedOperationException("Not needed for ClassChangeAgent storage tests");
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer) {
            throw new UnsupportedOperationException("Not needed for ClassChangeAgent storage tests");
        }

        @Override
        public boolean removeTransformer(ClassFileTransformer transformer) {
            throw new UnsupportedOperationException("Not needed for ClassChangeAgent storage tests");
        }

        @Override
        public boolean isRetransformClassesSupported() {
            return false;
        }

        @Override
        public void retransformClasses(Class<?>... classes) throws UnmodifiableClassException {
            throw new UnsupportedOperationException("Not needed for ClassChangeAgent storage tests");
        }

        @Override
        public boolean isRedefineClassesSupported() {
            return false;
        }

        @Override
        public void redefineClasses(ClassDefinition... definitions)
                throws ClassNotFoundException, UnmodifiableClassException {
            throw new UnsupportedOperationException("Not needed for ClassChangeAgent storage tests");
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
            throw new UnsupportedOperationException("Not needed for ClassChangeAgent storage tests");
        }

        @Override
        public void appendToBootstrapClassLoaderSearch(JarFile jarfile) {
            throw new UnsupportedOperationException("Not needed for ClassChangeAgent storage tests");
        }

        @Override
        public void appendToSystemClassLoaderSearch(JarFile jarfile) {
            throw new UnsupportedOperationException("Not needed for ClassChangeAgent storage tests");
        }

        @Override
        public boolean isNativeMethodPrefixSupported() {
            return false;
        }

        @Override
        public void setNativeMethodPrefix(ClassFileTransformer transformer, String prefix) {
            throw new UnsupportedOperationException("Not needed for ClassChangeAgent storage tests");
        }

        @Override
        public void redefineModule(
                Module module,
                Set<Module> extraReads,
                Map<String, Set<Module>> extraExports,
                Map<String, Set<Module>> extraOpens,
                Set<Class<?>> extraUses,
                Map<Class<?>, List<Class<?>>> extraProvides) {
            throw new UnsupportedOperationException("Not needed for ClassChangeAgent storage tests");
        }

        @Override
        public boolean isModifiableModule(Module module) {
            return false;
        }
    }
}
