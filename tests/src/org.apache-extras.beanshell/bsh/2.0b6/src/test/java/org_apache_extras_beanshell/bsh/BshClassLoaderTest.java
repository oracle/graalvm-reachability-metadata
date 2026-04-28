/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import bsh.classpath.BshClassLoader;
import bsh.classpath.ClassManagerImpl;
import java.net.URL;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BshClassLoaderTest {

    @Test
    public void loadClassDelegatesToClassManagerBaseLoaderWhenNoLocalUrlMatches() throws ClassNotFoundException {
        ClassManagerImpl classManager = new ClassManagerImpl();
        classManager.setClassPath(new URL[0]);
        BshClassLoader classLoader = new BshClassLoader(classManager, new URL[0]);

        Class<?> loadedClass = classLoader.loadClass(BshClassLoaderTest.class.getName(), false);

        assertThat(loadedClass).isEqualTo(BshClassLoaderTest.class);
    }
}
