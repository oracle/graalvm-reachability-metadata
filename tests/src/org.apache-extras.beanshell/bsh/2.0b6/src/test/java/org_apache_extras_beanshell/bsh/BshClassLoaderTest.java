/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import bsh.Interpreter;
import bsh.classpath.BshClassLoader;
import bsh.classpath.ClassManagerImpl;
import java.net.URL;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BshClassLoaderTest {

    @Test
    public void loadClassDelegatesToDesignatedLoaderForGeneratedScriptClass() throws Exception {
        String scriptedClassName = "GeneratedBshClassLoaderCoverage";
        Interpreter interpreter = new Interpreter();
        interpreter.eval("class " + scriptedClassName + " { public String value() { return \"loaded\"; } }");
        BshClassLoader classLoader = new BshClassLoader(interpreter.getClassManager(), new URL[0]);

        Class<?> loadedClass = classLoader.loadClass(scriptedClassName);

        assertThat(loadedClass.getName()).isEqualTo(scriptedClassName);
    }

    @Test
    public void loadClassDelegatesToClassManagerBaseLoaderWhenNoLocalUrlMatches() throws ClassNotFoundException {
        RecordingClassLoader baseLoader = new RecordingClassLoader();
        ClassManagerImpl classManager = new RecordingBaseLoaderClassManager(baseLoader);
        BshClassLoader classLoader = new BshClassLoader(classManager, new URL[0]);

        Class<?> loadedClass = classLoader.loadClass(BshClassLoaderTest.class.getName());

        assertThat(loadedClass).isEqualTo(BshClassLoaderTest.class);
        assertThat(baseLoader.loadedClassName).isEqualTo(BshClassLoaderTest.class.getName());
    }

    private static class RecordingBaseLoaderClassManager extends ClassManagerImpl {

        private final ClassLoader baseLoader;

        RecordingBaseLoaderClassManager(ClassLoader baseLoader) {
            this.baseLoader = baseLoader;
        }

        @Override
        public ClassLoader getBaseLoader() {
            return baseLoader;
        }
    }

    private static class RecordingClassLoader extends ClassLoader {

        private String loadedClassName;

        RecordingClassLoader() {
            super(BshClassLoaderTest.class.getClassLoader());
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            loadedClassName = name;
            if (BshClassLoaderTest.class.getName().equals(name)) {
                return BshClassLoaderTest.class;
            }
            return super.loadClass(name);
        }
    }
}
