/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_instrument;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.instrument.InstrumentationSavingAgent;

import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.SAME_THREAD)
public class Spring_instrumentTest {
    @Test
    void premainMakesInstrumentationAvailable() {
        Instrumentation instrumentation = new TestInstrumentation("premain");

        InstrumentationSavingAgent.premain("startup options", instrumentation);

        assertThat(InstrumentationSavingAgent.getInstrumentation()).isSameAs(instrumentation);
    }

    @Test
    void agentmainReplacesExistingInstrumentation() {
        Instrumentation initialInstrumentation = new TestInstrumentation("initial");
        Instrumentation replacementInstrumentation = new TestInstrumentation("replacement");

        InstrumentationSavingAgent.premain("initial options", initialInstrumentation);
        InstrumentationSavingAgent.agentmain("attach options", replacementInstrumentation);

        assertThat(InstrumentationSavingAgent.getInstrumentation()).isSameAs(replacementInstrumentation);
    }

    @Test
    void premainCanClearSavedInstrumentationWhenGivenNull() {
        InstrumentationSavingAgent.agentmain("attach options", new TestInstrumentation("temporary"));

        InstrumentationSavingAgent.premain("clear", null);

        assertThat(InstrumentationSavingAgent.getInstrumentation()).isNull();
    }

    @Test
    void agentmainCanClearSavedInstrumentationWhenGivenNull() {
        InstrumentationSavingAgent.premain("startup options", new TestInstrumentation("temporary"));

        InstrumentationSavingAgent.agentmain("clear", null);

        assertThat(InstrumentationSavingAgent.getInstrumentation()).isNull();
    }

    @Test
    void savedInstrumentationIsVisibleToOtherThreads() throws Exception {
        Instrumentation instrumentation = new TestInstrumentation("shared");
        CountDownLatch readerStarted = new CountDownLatch(1);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<Instrumentation> visibleInstrumentation = executorService.submit(() -> {
                readerStarted.countDown();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                Instrumentation currentInstrumentation;
                do {
                    currentInstrumentation = InstrumentationSavingAgent.getInstrumentation();
                    if (currentInstrumentation == instrumentation) {
                        return currentInstrumentation;
                    }
                    Thread.yield();
                } while (System.nanoTime() < deadline);
                return currentInstrumentation;
            });

            assertThat(readerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            InstrumentationSavingAgent.agentmain("publish to reader", instrumentation);

            assertThat(visibleInstrumentation.get(5, TimeUnit.SECONDS)).isSameAs(instrumentation);
        } finally {
            InstrumentationSavingAgent.premain("clear", null);
            executorService.shutdownNow();
        }
    }

    private static final class TestInstrumentation implements Instrumentation {
        private final String name;

        private TestInstrumentation(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer, boolean canRetransform) {
            throw unsupportedOperation();
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer) {
            throw unsupportedOperation();
        }

        @Override
        public boolean removeTransformer(ClassFileTransformer transformer) {
            throw unsupportedOperation();
        }

        @Override
        public boolean isRetransformClassesSupported() {
            return false;
        }

        @Override
        public void retransformClasses(Class<?>... classes) throws UnmodifiableClassException {
            throw unsupportedOperation();
        }

        @Override
        public boolean isRedefineClassesSupported() {
            return false;
        }

        @Override
        public void redefineClasses(ClassDefinition... definitions)
                throws ClassNotFoundException, UnmodifiableClassException {
            throw unsupportedOperation();
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
            return 0L;
        }

        @Override
        public void appendToBootstrapClassLoaderSearch(JarFile jarfile) {
            throw unsupportedOperation();
        }

        @Override
        public void appendToSystemClassLoaderSearch(JarFile jarfile) {
            throw unsupportedOperation();
        }

        @Override
        public boolean isNativeMethodPrefixSupported() {
            return false;
        }

        @Override
        public void setNativeMethodPrefix(ClassFileTransformer transformer, String prefix) {
            throw unsupportedOperation();
        }

        @Override
        public void redefineModule(Module module,
                Set<Module> extraReads,
                Map<String, Set<Module>> extraExports,
                Map<String, Set<Module>> extraOpens,
                Set<Class<?>> extraUses,
                Map<Class<?>, List<Class<?>>> extraProvides) {
            throw unsupportedOperation();
        }

        @Override
        public boolean isModifiableModule(Module module) {
            return false;
        }

        private static UnsupportedOperationException unsupportedOperation() {
            return new UnsupportedOperationException("Test instrumentation does not implement this operation");
        }
    }
}
