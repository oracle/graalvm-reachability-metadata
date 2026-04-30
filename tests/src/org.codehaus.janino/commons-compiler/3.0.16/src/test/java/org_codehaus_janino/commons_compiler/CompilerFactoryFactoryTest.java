/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import org.codehaus.commons.compiler.AbstractCompilerFactory;
import org.codehaus.commons.compiler.AbstractJavaSourceClassLoader;
import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.commons.compiler.IClassBodyEvaluator;
import org.codehaus.commons.compiler.ICompiler;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompilerFactoryFactoryTest extends AbstractCompilerFactory {
    private static final String FACTORY_ID = "test-commons-compiler-factory";

    @Test
    void discoversCompilerFactoriesFromClassNamesAndClasspathResources() throws Exception {
        ICompilerFactory explicitFactory = CompilerFactoryFactory.getCompilerFactory(
            CompilerFactoryFactoryTest.class.getName()
        );
        ICompilerFactory defaultFactory = CompilerFactoryFactory.getDefaultCompilerFactory();
        ICompilerFactory[] allFactories = CompilerFactoryFactory.getAllCompilerFactories();

        assertThat(explicitFactory).isInstanceOf(CompilerFactoryFactoryTest.class);
        assertThat(explicitFactory.getId()).isEqualTo(FACTORY_ID);
        assertThat(defaultFactory.getId()).isEqualTo(FACTORY_ID);
        assertThat(allFactories).extracting(ICompilerFactory::getId).contains(FACTORY_ID);
    }

    @Override
    public String getId() {
        return FACTORY_ID;
    }

    @Override
    public String toString() {
        return "Test commons-compiler factory";
    }

    @Override
    public String getImplementationVersion() {
        return "test";
    }

    @Override
    public ICompiler newCompiler() {
        throw new UnsupportedOperationException(FACTORY_ID + ": newCompiler");
    }

    @Override
    public IClassBodyEvaluator newClassBodyEvaluator() {
        return new ClassBodyDemoTest.PreparedClassBodyEvaluator();
    }

    @Override
    public AbstractJavaSourceClassLoader newJavaSourceClassLoader() {
        return new TestJavaSourceClassLoader();
    }
}
