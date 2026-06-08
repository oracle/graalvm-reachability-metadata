/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.instrument.ClassFileTransformer;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.springframework.instrument.classloading.websphere.WebSphereLoadTimeWeaver;

public class WebSphereClassLoaderAdapterTest {

    @Test
    void webSphereLoadTimeWeaverAdaptsWebSphereClassLoaderApi() throws Exception {
        TestCompoundClassLoader classLoader = new TestCompoundClassLoader(getClass().getClassLoader());

        WebSphereLoadTimeWeaver weaver = new WebSphereLoadTimeWeaver(classLoader);

        assertThat(weaver.getInstrumentableClassLoader()).isSameAs(classLoader);

        byte[] originalBytes = new byte[] {1, 2, 3};
        byte[] transformedBytes = new byte[] {4, 5, 6};
        TestClassFileTransformer transformer = new TestClassFileTransformer(
                classLoader, originalBytes, transformedBytes);

        weaver.addTransformer(transformer);

        assertThat(classLoader.getPreDefinePlugins()).hasSize(1);
        TestClassLoaderInstancePreDefinePlugin plugin = classLoader.getPreDefinePlugins().get(0);
        byte[] result = plugin.transformClass("example.InstrumentedType", originalBytes, null, classLoader);
        assertThat(result).isSameAs(transformedBytes);
        assertThat(transformer.transformedInstrumentedType()).isTrue();

        ClassLoader throwawayClassLoader = weaver.getThrowawayClassLoader();

        assertThat(throwawayClassLoader).isNotNull().isNotSameAs(classLoader);
        assertThat(TestCompoundClassLoader.getLastClonedFrom()).isSameAs(classLoader);
        assertThat(TestCompoundClassLoader.getLastClone().getPreDefinePlugins()).isEmpty();
        assertThat(classLoader.getPreDefinePlugins()).containsExactly(plugin);
    }

    private static final class TestClassFileTransformer implements ClassFileTransformer {

        private final ClassLoader expectedLoader;

        private final byte[] expectedOriginalBytes;

        private final byte[] transformedBytes;

        private final AtomicBoolean transformedInstrumentedType = new AtomicBoolean();

        TestClassFileTransformer(ClassLoader expectedLoader, byte[] expectedOriginalBytes, byte[] transformedBytes) {
            this.expectedLoader = expectedLoader;
            this.expectedOriginalBytes = expectedOriginalBytes;
            this.transformedBytes = transformedBytes;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if (!"example/InstrumentedType".equals(className)) {
                return null;
            }
            assertThat(loader).isSameAs(this.expectedLoader);
            assertThat(classBeingRedefined).isNull();
            assertThat(protectionDomain).isNull();
            assertThat(classfileBuffer).isSameAs(this.expectedOriginalBytes);
            this.transformedInstrumentedType.set(true);
            return this.transformedBytes;
        }

        boolean transformedInstrumentedType() {
            return this.transformedInstrumentedType.get();
        }
    }

    public interface TestClassLoaderInstancePreDefinePlugin {

        byte[] transformClass(String className, byte[] classBytes, CodeSource codeSource, ClassLoader classLoader);
    }

    public static class TestCompoundClassLoader extends FakeCompoundClassLoader {

        private static final String COMPOUND_CLASS_LOADER_NAME =
                "com.ibm.ws.classloader.CompoundClassLoader";

        private static final String CLASS_PRE_DEFINE_PLUGIN_NAME =
                "com.ibm.websphere.classloader.ClassLoaderInstancePreDefinePlugin";

        private static final AtomicReference<TestCompoundClassLoader> lastClone = new AtomicReference<>();

        private static final AtomicReference<FakeCompoundClassLoader> lastClonedFrom = new AtomicReference<>();

        public TestCompoundClassLoader(ClassLoader parent) {
            super(parent);
        }

        public TestCompoundClassLoader(FakeCompoundClassLoader original) {
            super(original.getParent());
            getPreDefinePlugins().addAll(original.getPreDefinePlugins());
            lastClone.set(this);
            lastClonedFrom.set(original);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (COMPOUND_CLASS_LOADER_NAME.equals(name)) {
                return FakeCompoundClassLoader.class;
            }
            if (CLASS_PRE_DEFINE_PLUGIN_NAME.equals(name)) {
                return TestClassLoaderInstancePreDefinePlugin.class;
            }
            return super.loadClass(name);
        }

        public void addPreDefinePlugin(TestClassLoaderInstancePreDefinePlugin plugin) {
            getPreDefinePlugins().add(plugin);
        }

        static TestCompoundClassLoader getLastClone() {
            return lastClone.get();
        }

        static FakeCompoundClassLoader getLastClonedFrom() {
            return lastClonedFrom.get();
        }
    }

    public static class FakeCompoundClassLoader extends ClassLoader {

        private final List<TestClassLoaderInstancePreDefinePlugin> preDefinePlugins = new ArrayList<>();

        FakeCompoundClassLoader(ClassLoader parent) {
            super(parent);
        }

        List<TestClassLoaderInstancePreDefinePlugin> getPreDefinePlugins() {
            return this.preDefinePlugins;
        }
    }
}
