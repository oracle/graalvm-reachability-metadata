/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_osgi_bundle;

import static org.assertj.core.api.Assertions.assertThat;

import javassist.ClassPool;
import javassist.Loader;

import org.junit.jupiter.api.Test;

public class JavassistLoaderTest {
    @Test
    void runLoadsParentDelegatedClassAndInvokesMainMethod() throws Throwable {
        RunTarget.arguments = null;
        ExposedLoader loader = new ExposedLoader(JavassistLoaderTest.class.getClassLoader());
        loader.delegateLoadingOf(RunTarget.class.getName());

        loader.run(RunTarget.class.getName(), new String[] {"alpha", "beta"});

        assertThat(RunTarget.arguments).containsExactly("alpha", "beta");
    }

    @Test
    void delegateToParentUsesSystemClassLookupWhenParentIsNull() throws ClassNotFoundException {
        ExposedLoader loader = new ExposedLoader(null);

        Class<?> loadedClass = loader.callDelegateToParent(String.class.getName());

        assertThat(loadedClass).isSameAs(String.class);
    }

    @Test
    void findClassAttemptsToReadClassResourceWhenNoClassPoolIsConfigured() throws ClassNotFoundException {
        ExposedLoader loader = new ExposedLoader(null);

        Class<?> loadedClass = loader.callFindClass("org_jboss_weld.weld_osgi_bundle.DoesNotExist");

        assertThat(loadedClass).isNull();
    }

    private static final class ExposedLoader extends Loader {
        private ExposedLoader(ClassLoader parent) {
            super(parent, (ClassPool) null);
        }

        private Class<?> callDelegateToParent(String className) throws ClassNotFoundException {
            return delegateToParent(className);
        }

        private Class<?> callFindClass(String className) throws ClassNotFoundException {
            return findClass(className);
        }
    }

    public static final class RunTarget {
        private static String[] arguments;

        public static void main(String[] args) {
            arguments = args;
        }
    }
}
