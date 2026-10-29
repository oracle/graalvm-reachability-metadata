/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.taskdefs.compilers.CompilerAdapterFactory;
import org.apache.tools.ant.taskdefs.compilers.JavacExternal;
import org.junit.jupiter.api.Test;

public class CompilerAdapterFactoryTest {
    @Test
    void resolvesExplicitCompilerAdapterClass() {
        assertInstanceOf(JavacExternal.class, CompilerAdapterFactory.getCompiler(
                "org.apache.tools.ant.taskdefs.compilers.JavacExternal", new Javac()));
    }
}
