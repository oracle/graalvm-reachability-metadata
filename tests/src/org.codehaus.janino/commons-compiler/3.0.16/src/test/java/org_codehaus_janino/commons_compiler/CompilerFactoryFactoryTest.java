/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.junit.jupiter.api.Test;

public class CompilerFactoryFactoryTest {
    private static final String JANINO_COMPILER_FACTORY = "org.codehaus.janino.CompilerFactory";

    @Test
    public void discoversAndInstantiatesCompilerFactoriesFromClasspath() throws Exception {
        ICompilerFactory namedFactory = CompilerFactoryFactory.getCompilerFactory(JANINO_COMPILER_FACTORY);
        ICompilerFactory defaultFactory = CompilerFactoryFactory.getDefaultCompilerFactory();
        ICompilerFactory[] factories = CompilerFactoryFactory.getAllCompilerFactories();

        assertThat(namedFactory.getClass().getName()).isEqualTo(JANINO_COMPILER_FACTORY);
        assertThat(defaultFactory.getClass().getName()).isEqualTo(JANINO_COMPILER_FACTORY);
        assertThat(factories)
            .extracting(factory -> factory.getClass().getName())
            .contains(JANINO_COMPILER_FACTORY);
    }
}
