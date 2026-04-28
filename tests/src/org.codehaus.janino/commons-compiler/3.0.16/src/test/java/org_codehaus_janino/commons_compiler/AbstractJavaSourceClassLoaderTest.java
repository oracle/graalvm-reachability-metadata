/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.commons.compiler.AbstractJavaSourceClassLoader;
import org.junit.jupiter.api.Test;

public class AbstractJavaSourceClassLoaderTest {

    @Test
    void loadsTheTargetClassAndInvokesItsMainMethod() throws Exception {
        CompilerFactoryFactoryTest.JavaSourceMain.reset();

        try (CompilerFactoryFactoryTest.ContextClassLoaderScope ignored =
                     CompilerFactoryFactoryTest.withCompilerFactoryResources(1)) {
            AbstractJavaSourceClassLoader.main(new String[]{
                    CompilerFactoryFactoryTest.JavaSourceMain.class.getName(),
                    "first",
                    "second"
            });
        }

        assertThat(CompilerFactoryFactoryTest.JavaSourceMain.arguments()).containsExactly("first", "second");
    }
}
