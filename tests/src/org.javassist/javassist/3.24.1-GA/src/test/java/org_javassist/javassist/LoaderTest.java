/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javassist.Loader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class LoaderTest {
    private static final String INVALID_RESOURCE_CLASS_NAME = "org_javassist.javassist.generated.InvalidClassResource";

    @AfterEach
    void resetRunTargetState() {
        RunTarget.receivedArguments = new String[0];
    }

    @Test
    void runDelegatesTargetClassLoadingAndInvokesMainMethod() throws Throwable {
        Loader loader = new Loader(LoaderTest.class.getClassLoader(), null);
        loader.delegateLoadingOf(RunTarget.class.getName());

        loader.run(RunTarget.class.getName(), new String[] {"alpha", "beta"});

        assertThat(RunTarget.receivedArguments).containsExactly("alpha", "beta");
    }

    @Test
    void loadClassDelegatesConfiguredPackagesToParentClassLoader() throws Exception {
        Loader loader = new Loader(LoaderTest.class.getClassLoader(), null);
        loader.delegateLoadingOf("org_javassist.javassist.");

        Class<?> loadedClass = loader.loadClass(DelegatedTarget.class.getName());

        assertThat(loadedClass).isSameAs(DelegatedTarget.class);
    }

    @Test
    void loadClassUsesSystemClassLookupWhenNoParentClassLoaderIsAvailable() throws Exception {
        Loader loader = new Loader(null, null);

        Class<?> loadedClass = loader.loadClass(String.class.getName());

        assertThat(loadedClass).isSameAs(String.class);
    }

    @Test
    void findClassReadsClassBytesFromLoaderClassResource() {
        Loader loader = new Loader(LoaderTest.class.getClassLoader(), null);

        assertThatThrownBy(() -> loader.loadClass(INVALID_RESOURCE_CLASS_NAME))
                .isNotInstanceOf(ClassNotFoundException.class);
    }

    public static class RunTarget {
        private static String[] receivedArguments = new String[0];

        public static void main(String[] args) {
            receivedArguments = args.clone();
        }
    }

    public static class DelegatedTarget {
    }
}
