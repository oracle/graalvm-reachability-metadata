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

public class UtilsTest {
    @Test
    void loadsClassDirectlyWhenContextClassLoaderIsUnavailable() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        String className = CommonAST.class.getName();

        try {
            Thread.currentThread().setContextClassLoader(null);

            Class<?> loadedClass = Utils.loadClass(className);

            assertThat(loadedClass).isEqualTo(CommonAST.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void fallsBackToDirectClassLoadingWhenContextClassLoaderFails() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        String className = CommonAST.class.getName();
        ClassLoader rejectingClassLoader = new ClassLoader(originalClassLoader) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (className.equals(name)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name);
            }
        };

        try {
            Thread.currentThread().setContextClassLoader(rejectingClassLoader);

            Class<?> loadedClass = Utils.loadClass(className);

            assertThat(loadedClass).isEqualTo(CommonAST.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void createsInstanceFromLoadedClassName() throws Exception {
        Object instance = Utils.createInstanceOf(CommonAST.class.getName());

        assertThat(instance).isInstanceOf(CommonAST.class);
    }
}
