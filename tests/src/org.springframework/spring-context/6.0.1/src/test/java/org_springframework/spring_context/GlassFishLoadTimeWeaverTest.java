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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.instrument.classloading.glassfish.GlassFishLoadTimeWeaver;

public class GlassFishLoadTimeWeaverTest {

    @Test
    void glassFishLoadTimeWeaverAdaptsInstrumentableClassLoaderApi() {
        TestGlassFishClassLoader classLoader = new TestGlassFishClassLoader(getClass().getClassLoader());

        GlassFishLoadTimeWeaver weaver = new GlassFishLoadTimeWeaver(classLoader);

        assertThat(weaver.getInstrumentableClassLoader()).isSameAs(classLoader);
        assertThat(classLoader.loadedInstrumentableClassCount()).isEqualTo(1);

        ClassFileTransformer transformer = new TestClassFileTransformer();
        weaver.addTransformer(transformer);

        assertThat(classLoader.getTransformers()).containsExactly(transformer);

        ClassLoader throwawayClassLoader = weaver.getThrowawayClassLoader();

        assertThat(throwawayClassLoader).isNotNull().isNotSameAs(classLoader);
        assertThat(classLoader.copyInvocations()).isEqualTo(1);
        assertThat(classLoader.getLastCopy()).isNotNull().isNotSameAs(classLoader);
        assertThat(classLoader.getLastCopy().getParent()).isSameAs(classLoader.getParent());
    }

    private static final class TestClassFileTransformer implements ClassFileTransformer {

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            return null;
        }
    }

    public interface TestInstrumentableClassLoader {

        void addTransformer(ClassFileTransformer transformer);

        ClassLoader copy();
    }

    public static final class TestGlassFishClassLoader extends ClassLoader implements TestInstrumentableClassLoader {

        private static final String INSTRUMENTABLE_CLASS_LOADER_NAME =
                "org.glassfish.api.deployment.InstrumentableClassLoader";

        private final AtomicInteger loadedInstrumentableClassCount = new AtomicInteger();

        private final AtomicInteger copyInvocations = new AtomicInteger();

        private final List<ClassFileTransformer> transformers = new ArrayList<>();

        private ClassLoader lastCopy;

        TestGlassFishClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (INSTRUMENTABLE_CLASS_LOADER_NAME.equals(name)) {
                this.loadedInstrumentableClassCount.incrementAndGet();
                return TestInstrumentableClassLoader.class;
            }
            return super.loadClass(name);
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer) {
            this.transformers.add(transformer);
        }

        @Override
        public ClassLoader copy() {
            this.copyInvocations.incrementAndGet();
            this.lastCopy = new ClassLoader(getParent()) {
            };
            return this.lastCopy;
        }

        int loadedInstrumentableClassCount() {
            return this.loadedInstrumentableClassCount.get();
        }

        int copyInvocations() {
            return this.copyInvocations.get();
        }

        List<ClassFileTransformer> getTransformers() {
            return this.transformers;
        }

        ClassLoader getLastCopy() {
            return this.lastCopy;
        }
    }
}
