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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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

    @Test
    void storedInstrumentationIsAvailableToApplicationThreads() throws InterruptedException {
        RecordingInstrumentation instrumentation = new RecordingInstrumentation();
        CountDownLatch readCompleted = new CountDownLatch(1);
        AtomicReference<Instrumentation> observedInstrumentation = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            observedInstrumentation.set(ClassChangeAgent.getInstrumentation());
            readCompleted.countDown();
        });

        ClassChangeAgent.premain(null, instrumentation);
        reader.start();

        assertThat(readCompleted.await(5, TimeUnit.SECONDS)).isTrue();
        reader.join();
        assertThat(observedInstrumentation).hasValue(instrumentation);
    }

    @Test
    void retrievedInstrumentationCanBeUsedForClassChangeCapabilityQueries() {
        Class<?>[] loadedClasses = {ClassChangeAgent.class, Quarkus_class_change_agentTest.class};
        RecordingInstrumentation instrumentation = new RecordingInstrumentation(
                true, Set.of(ClassChangeAgent.class), loadedClasses);

        ClassChangeAgent.premain("class-change-capabilities", instrumentation);
        Instrumentation retrievedInstrumentation = ClassChangeAgent.getInstrumentation();

        assertThat(retrievedInstrumentation.isRedefineClassesSupported()).isTrue();
        assertThat(retrievedInstrumentation.isModifiableClass(ClassChangeAgent.class)).isTrue();
        assertThat(retrievedInstrumentation.isModifiableClass(String.class)).isFalse();
        assertThat(retrievedInstrumentation.getAllLoadedClasses()).containsExactly(loadedClasses);
    }

    private static final class RecordingInstrumentation implements Instrumentation {
        private final boolean redefineClassesSupported;
        private final Set<Class<?>> modifiableClasses;
        private final Class<?>[] allLoadedClasses;

        private RecordingInstrumentation() {
            this(false, Set.of(), new Class<?>[0]);
        }

        private RecordingInstrumentation(
                boolean redefineClassesSupported, Set<Class<?>> modifiableClasses, Class<?>[] allLoadedClasses) {
            this.redefineClassesSupported = redefineClassesSupported;
            this.modifiableClasses = modifiableClasses;
            this.allLoadedClasses = allLoadedClasses;
        }

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
            return redefineClassesSupported;
        }

        @Override
        public void redefineClasses(ClassDefinition... definitions)
                throws ClassNotFoundException, UnmodifiableClassException {
            throw new UnsupportedOperationException("Not needed for ClassChangeAgent storage tests");
        }

        @Override
        public boolean isModifiableClass(Class<?> theClass) {
            return modifiableClasses.contains(theClass);
        }

        @Override
        public Class<?>[] getAllLoadedClasses() {
            return allLoadedClasses;
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
