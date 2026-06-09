/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewMethod;
import javassist.Loader;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class LoaderTest {
    private static final String GENERATED_APPLICATION_NAME =
            "example.LoaderGeneratedApplication";

    @Test
    void runsGeneratedMainClassFromClassPool() throws Throwable {
        ClassPool classPool = newInitializedClassPool();
        CtClass application = makeGeneratedApplication(classPool);
        Loader loader = new Loader(Thread.currentThread().getContextClassLoader(), classPool);

        try {
            loader.run(GENERATED_APPLICATION_NAME, new String[] {"left", "right"});
        } catch (Error error) {
            rethrowUnlessUnsupportedDynamicClassLoading(error);
        } finally {
            application.detach();
        }
    }

    @Test
    void delegatesConfiguredClassLoadingToParentLoader() throws Exception {
        Loader loader = new Loader(newInitializedClassPool());
        loader.delegateLoadingOf(LoaderResourceFixture.class.getName());

        Class<?> loadedClass = loader.loadClass(LoaderResourceFixture.class.getName());

        assertThat(loadedClass).isSameAs(LoaderResourceFixture.class);
    }

    @Test
    void delegatesSystemClassLoadingWhenParentIsBootstrapLoader() throws Exception {
        Loader loader = new Loader(null, newInitializedClassPool());

        Class<?> loadedClass = loader.loadClass(String.class.getName());

        assertThat(loadedClass).isSameAs(String.class);
    }

    @Test
    void findsClassBytesAsResourcesWhenNoClassPoolIsConfigured() throws Exception {
        Loader loader = new Loader();

        try {
            Class<?> loadedClass = loader.loadClass(LoaderResourceFixture.class.getName());

            assertThat(loadedClass.getName()).isEqualTo(LoaderResourceFixture.class.getName());
            assertThat(loadedClass.getClassLoader()).isSameAs(loader);
        } catch (Error error) {
            rethrowUnlessUnsupportedDynamicClassLoading(error);
        }
    }

    private static ClassPool newInitializedClassPool() {
        ClassPool classPool = new ClassPool(true);
        classPool.insertClassPath(new ClassClassPath(LoaderTest.class));
        return classPool;
    }

    private static CtClass makeGeneratedApplication(ClassPool classPool) throws Exception {
        CtClass application = classPool.makeClass(GENERATED_APPLICATION_NAME);
        application.addMethod(CtNewMethod.make(
                """
                public static void main(String[] args) {
                    if (args.length != 2) {
                        throw new IllegalArgumentException("expected two arguments");
                    }
                }
                """,
                application));
        return application;
    }

    private static void rethrowUnlessUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}

final class LoaderResourceFixture {
    private LoaderResourceFixture() {
    }
}
