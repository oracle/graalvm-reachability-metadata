/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import bsh.Interpreter;
import bsh.classpath.BshClassLoader;
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


}
