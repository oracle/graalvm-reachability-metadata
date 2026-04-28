/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import javassist.Loader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LoaderTest {
    @Test
    void runsDelegatedMainClassWithArguments() throws Throwable {
        InvokedMain.reset();
        Loader loader = new Loader(LoaderTest.class.getClassLoader(), null);
        loader.delegateLoadingOf(InvokedMain.class.getName());

        loader.run(InvokedMain.class.getName(), new String[] {"alpha", "beta"});

        assertThat(InvokedMain.invocationCount()).isEqualTo(1);
        assertThat(InvokedMain.arguments()).containsExactly("alpha", "beta");
    }

    @Test
    void delegatesPlatformClassLoadingToParentLoader() throws ClassNotFoundException {
        Loader loader = new Loader(LoaderTest.class.getClassLoader(), null);

        Class<?> loaded = loader.loadClass(String.class.getName());

        assertThat(loaded).isSameAs(String.class);
    }

    @Test
    void delegatesPlatformClassLoadingToSystemLoaderWhenParentIsAbsent() throws ClassNotFoundException {
        Loader loader = new Loader(null, null);

        Class<?> loaded = loader.loadClass(String.class.getName());

        assertThat(loaded).isSameAs(String.class);
    }

    @Test
    void attemptsOwnClassResourceLookupBeforeParentFallback() {
        Loader loader = new Loader(LoaderTest.class.getClassLoader(), null);

        assertThatThrownBy(() -> loader.loadClass("javassist_javassist.loader.MissingClass"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    public static class InvokedMain {
        private static int invocationCount;
        private static String[] arguments = new String[0];

        public static void main(String[] args) {
            invocationCount++;
            arguments = args.clone();
        }

        static void reset() {
            invocationCount = 0;
            arguments = new String[0];
        }

        static int invocationCount() {
            return invocationCount;
        }

        static String[] arguments() {
            return arguments.clone();
        }
    }
}
