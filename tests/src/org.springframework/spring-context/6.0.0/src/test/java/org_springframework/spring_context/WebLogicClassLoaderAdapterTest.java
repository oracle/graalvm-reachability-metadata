/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.springframework.instrument.classloading.weblogic.WebLogicLoadTimeWeaver;

public class WebLogicClassLoaderAdapterTest {

    @Test
    void webLogicLoadTimeWeaverAdaptsWebLogicClassLoaderApi() {
        TestClassFinder classFinder = new TestClassFinder("application");
        TestGenericClassLoader classLoader = new TestGenericClassLoader(classFinder, getClass().getClassLoader());

        WebLogicLoadTimeWeaver weaver = new WebLogicLoadTimeWeaver(classLoader);

        assertThat(weaver.getInstrumentableClassLoader()).isSameAs(classLoader);

        byte[] originalBytes = new byte[] {1, 2, 3};
        byte[] transformedBytes = new byte[] {4, 5, 6};
        ClassFileTransformer transformer = new TestClassFileTransformer(classLoader, originalBytes, transformedBytes);

        weaver.addTransformer(transformer);

        assertThat(classLoader.getPreProcessors()).hasSize(1);
        TestClassPreProcessor preProcessor = classLoader.getPreProcessors().get(0);
        preProcessor.initialize(null);
        assertThat(preProcessor.preProcess("example/InstrumentedType", originalBytes)).isSameAs(transformedBytes);

        int constructorInvocations = TestGenericClassLoader.getConstructorInvocations();
        ClassLoader throwawayClassLoader = weaver.getThrowawayClassLoader();

        assertThat(throwawayClassLoader).isNotNull().isNotSameAs(classLoader);
        assertThat(TestGenericClassLoader.getConstructorInvocations()).isEqualTo(constructorInvocations + 1);
        assertThat(TestGenericClassLoader.getLastConstructedClassFinder()).isSameAs(classFinder);
        assertThat(TestGenericClassLoader.getLastConstructedParent()).isSameAs(classLoader.getParent());
    }

    private static final class TestClassFileTransformer implements ClassFileTransformer {

        private final ClassLoader expectedLoader;

        private final byte[] expectedOriginalBytes;

        private final byte[] transformedBytes;

        TestClassFileTransformer(ClassLoader expectedLoader, byte[] expectedOriginalBytes, byte[] transformedBytes) {
            this.expectedLoader = expectedLoader;
            this.expectedOriginalBytes = expectedOriginalBytes;
            this.transformedBytes = transformedBytes;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            assertThat(loader).isSameAs(this.expectedLoader);
            assertThat(className).isEqualTo("example/InstrumentedType");
            assertThat(classBeingRedefined).isNull();
            assertThat(protectionDomain).isNull();
            assertThat(classfileBuffer).isSameAs(this.expectedOriginalBytes);
            return this.transformedBytes;
        }
    }

    public interface TestClassPreProcessor {

        void initialize(Hashtable<?, ?> params);

        byte[] preProcess(String className, byte[] classBytes);
    }

    public static final class TestClassFinder {

        private final String name;

        TestClassFinder(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    }

    public static class TestGenericClassLoader extends ClassLoader {

        private static final String GENERIC_CLASS_LOADER_NAME =
                "weblogic.utils.classloaders.GenericClassLoader";

        private static final String CLASS_PRE_PROCESSOR_NAME =
                "weblogic.utils.classloaders.ClassPreProcessor";

        private static final AtomicInteger constructorInvocations = new AtomicInteger();

        private static final AtomicReference<TestClassFinder> lastConstructedClassFinder = new AtomicReference<>();

        private static final AtomicReference<ClassLoader> lastConstructedParent = new AtomicReference<>();

        private final TestClassFinder classFinder;

        private final List<TestClassPreProcessor> preProcessors = new ArrayList<>();

        public TestGenericClassLoader(TestClassFinder classFinder, ClassLoader parent) {
            super(parent);
            this.classFinder = classFinder;
            constructorInvocations.incrementAndGet();
            lastConstructedClassFinder.set(classFinder);
            lastConstructedParent.set(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (GENERIC_CLASS_LOADER_NAME.equals(name)) {
                return TestGenericClassLoader.class;
            }
            if (CLASS_PRE_PROCESSOR_NAME.equals(name)) {
                return TestClassPreProcessor.class;
            }
            return super.loadClass(name);
        }

        public void addInstanceClassPreProcessor(TestClassPreProcessor preProcessor) {
            this.preProcessors.add(preProcessor);
        }

        public TestClassFinder getClassFinder() {
            return this.classFinder;
        }

        public List<TestClassPreProcessor> getPreProcessors() {
            return this.preProcessors;
        }

        static int getConstructorInvocations() {
            return constructorInvocations.get();
        }

        static TestClassFinder getLastConstructedClassFinder() {
            return lastConstructedClassFinder.get();
        }

        static ClassLoader getLastConstructedParent() {
            return lastConstructedParent.get();
        }
    }
}
