/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import javassist.Loader;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoaderTest {
    private static final String RUN_RESULT_PROPERTY = "javassist.loader.test.result";

    @Test
    void runLoadsApplicationClassAndInvokesMainMethod() throws Throwable {
        System.clearProperty(RUN_RESULT_PROPERTY);
        Loader loader = new Loader();

        try {
            loader.run(LoaderRunnableApplication.class.getName(), new String[] { "left", "right" });

            assertThat(System.getProperty(RUN_RESULT_PROPERTY)).isEqualTo("left:right");
        } catch (Error error) {
            verifyUnsupportedDynamicClassLoading(error);
        } finally {
            System.clearProperty(RUN_RESULT_PROPERTY);
        }
    }

    @Test
    void loadClassReadsClassResourceAndFindsPreviouslyLoadedClass() throws ClassNotFoundException {
        Loader loader = new Loader();

        try {
            Class<?> loadedClass = loader.loadClass(LoaderResourceApplication.class.getName());
            Class<?> cachedClass = loader.loadClass(LoaderResourceApplication.class.getName());

            assertThat(cachedClass).isSameAs(loadedClass);
            assertThat(loadedClass.getName()).isEqualTo(LoaderResourceApplication.class.getName());
        } catch (Error error) {
            verifyUnsupportedDynamicClassLoading(error);
        }
    }

    @Test
    void loadClassDelegatesJavaAndExplicitClassesToParentClassLoader() throws ClassNotFoundException {
        Loader loader = new Loader();
        loader.delegateLoadingOf(LoaderParentDelegatedApplication.class.getName());

        assertThat(loader.loadClass(String.class.getName())).isSameAs(String.class);
        assertThat(loader.loadClass(LoaderParentDelegatedApplication.class.getName()))
                .isSameAs(LoaderParentDelegatedApplication.class);
    }

    @Test
    void loadClassDelegatesToSystemClassLoaderWhenParentIsAbsent() throws ClassNotFoundException {
        Loader loader = new Loader(null, null);

        assertThat(loader.loadClass(Integer.class.getName())).isSameAs(Integer.class);
    }

    private static void verifyUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public static class LoaderRunnableApplication {
        public static void main(String[] args) {
            System.setProperty("javassist.loader.test.result", args[0] + ":" + args[1]);
        }
    }

    public static class LoaderResourceApplication {
    }

    public static class LoaderParentDelegatedApplication {
    }
}
