/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr.antlr;

import antlr.CommonAST;
import antlr.Utils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UtilsTest {
    @Test
    void loadClassUsesForNameWhenContextClassLoaderIsMissing() throws ClassNotFoundException {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);

        try {
            Class<?> loadedClass = Utils.loadClass("java.lang.String");

            assertThat(loadedClass).isEqualTo(String.class);
        }
        finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void loadClassFallsBackToForNameWhenContextClassLoaderFails() throws ClassNotFoundException {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) {
                throw new RuntimeException("simulated class loading failure");
            }
        });

        try {
            Class<?> loadedClass = Utils.loadClass("java.lang.String");

            assertThat(loadedClass).isEqualTo(String.class);
        }
        finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void createInstanceOfCreatesObjectFromLoadedClass() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        Object instance = Utils.createInstanceOf("antlr.CommonAST");

        assertThat(instance).isInstanceOf(CommonAST.class);
    }
}
