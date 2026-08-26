/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_compiler_manager;

import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.compiler.Compiler;
import org.codehaus.plexus.compiler.manager.CompilerManager;
import org.codehaus.plexus.compiler.manager.NoSuchCompilerException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Plexus_compiler_managerTest {
    @Test
    void looksUpJavacCompilerFromPlexusContainer() throws Exception {
        DefaultPlexusContainer container = createContainer();

        try {
            CompilerManager compilerManager = (CompilerManager) container.lookup(CompilerManager.ROLE);
            Compiler compiler = compilerManager.getCompiler("javac");

            assertThat(compiler).isNotNull();
        } finally {
            container.dispose();
        }
    }

    @Test
    void reportsUnknownCompilerId() throws Exception {
        DefaultPlexusContainer container = createContainer();

        try {
            CompilerManager compilerManager = (CompilerManager) container.lookup(CompilerManager.ROLE);

            assertThatThrownBy(() -> compilerManager.getCompiler("unknown"))
                    .isInstanceOf(NoSuchCompilerException.class)
                    .hasMessageContaining("unknown");
        } finally {
            container.dispose();
        }
    }

    private DefaultPlexusContainer createContainer() throws Exception {
        DefaultPlexusContainer container = new DefaultPlexusContainer();
        container.initialize();
        container.start();
        return container;
    }
}
