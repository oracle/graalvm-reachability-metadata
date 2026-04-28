/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import bsh.Interpreter;
import bsh.classpath.ClassManagerImpl;
import java.net.URL;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassManagerImplTest {

    private static final String MISSING_RESOURCE_PATH =
            "/org_apache_extras_beanshell/bsh/missing-class-manager-impl-resource.txt";

    @Test
    public void classForNameLoadsBeanShellClassesThroughInterpreterClassLoader() {
        ClassManagerImpl classManager = new ClassManagerImpl();

        Class<?> loadedClass = classManager.classForName(Interpreter.class.getName());

        assertThat(loadedClass).isEqualTo(Interpreter.class);
    }

    @Test
    public void classForNameUsesBaseLoaderAfterClassPathIsConfigured() {
        ClassManagerImpl classManager = new ClassManagerImpl();
        classManager.setClassPath(new URL[0]);

        Class<?> loadedClass = classManager.classForName(String.class.getName());

        assertThat(loadedClass).isEqualTo(String.class);
    }

    @Test
    public void classForNameDelegatesToConfiguredExternalClassLoader() {
        ClassManagerImpl classManager = new ClassManagerImpl();
        RecordingClassLoader classLoader = new RecordingClassLoader();
        classManager.setClassLoader(classLoader);

        Class<?> loadedClass = classManager.classForName(ExternalLoaderTarget.class.getName());

        assertThat(loadedClass).isEqualTo(ExternalLoaderTarget.class);
        assertThat(classLoader.loadedClassName).isEqualTo(ExternalLoaderTarget.class.getName());
    }

    @Test
    public void getResourceQueriesBaseLoaderAfterClassPathIsConfigured() {
        ClassManagerImpl classManager = new ClassManagerImpl();
        classManager.setClassPath(new URL[0]);

        URL resource = classManager.getResource(MISSING_RESOURCE_PATH);

        assertThat(resource).isNull();
    }

    public static class ExternalLoaderTarget {
    }

    private static class RecordingClassLoader extends ClassLoader {
        private String loadedClassName;

        RecordingClassLoader() {
            super(ClassManagerImplTest.class.getClassLoader());
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            loadedClassName = name;
            if (ExternalLoaderTarget.class.getName().equals(name)) {
                return ExternalLoaderTarget.class;
            }
            return super.loadClass(name);
        }
    }
}
