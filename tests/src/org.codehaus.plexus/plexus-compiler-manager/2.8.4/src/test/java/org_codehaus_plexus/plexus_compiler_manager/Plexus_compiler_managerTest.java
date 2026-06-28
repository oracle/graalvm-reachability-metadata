/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_compiler_manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.compiler.Compiler;
import org.codehaus.plexus.compiler.javac.JavacCompiler;
import org.codehaus.plexus.compiler.manager.CompilerManager;
import org.codehaus.plexus.compiler.manager.NoSuchCompilerException;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.junit.jupiter.api.Test;

public class Plexus_compiler_managerTest {
    @Test
    void compilerManagerRoleUsesPublicInterfaceNameForPlexusLookup() throws Exception {
        PlexusContainer container = new DefaultPlexusContainer();
        try {
            container.initialize();
            container.start();

            Object manager = container.lookup(CompilerManager.class.getName());

            assertThat(CompilerManager.ROLE).isEqualTo(CompilerManager.class.getName());
            assertThat(manager).isInstanceOf(CompilerManager.class);
        } finally {
            container.dispose();
        }
    }

    @Test
    void noSuchCompilerExceptionExposesRequestedCompilerIdentifier() {
        NoSuchCompilerException exception = new NoSuchCompilerException("ecj");

        assertThat(exception)
                .hasMessage("No such compiler 'ecj'.")
                .hasNoCause();
        assertThat(exception.getCompilerId()).isEqualTo("ecj");
    }

    @Test
    void plexusContainerLooksUpManagerAndReturnsRegisteredJavacCompiler() throws Exception {
        PlexusContainer container = new DefaultPlexusContainer();
        try {
            container.initialize();
            container.start();

            CompilerManager manager = (CompilerManager) container.lookup(CompilerManager.ROLE);
            Compiler compiler = manager.getCompiler("javac");

            assertThat(manager).isNotNull();
            assertThat(compiler).isInstanceOf(JavacCompiler.class);
        } finally {
            container.dispose();
        }
    }

    @Test
    void compilerManagerReturnsCompilerRegisteredWithAdditionalRoleHint() throws Exception {
        PlexusContainer container = new DefaultPlexusContainer();
        try {
            container.initialize();
            container.start();

            ComponentDescriptor descriptor = new ComponentDescriptor();
            descriptor.setRole(Compiler.ROLE);
            descriptor.setRoleHint("custom-javac");
            descriptor.setImplementation(JavacCompiler.class.getName());
            container.addComponentDescriptor(descriptor);

            CompilerManager manager = (CompilerManager) container.lookup(CompilerManager.ROLE);
            Compiler compiler = manager.getCompiler("custom-javac");

            assertThat(compiler).isSameAs(container.lookup(Compiler.ROLE, "custom-javac"));
            assertThat(compiler).isInstanceOf(JavacCompiler.class);
        } finally {
            container.dispose();
        }
    }

    @Test
    void plexusContainerManagedManagerReportsUnknownCompilerHint() throws Exception {
        PlexusContainer container = new DefaultPlexusContainer();
        try {
            container.initialize();
            container.start();

            CompilerManager manager = (CompilerManager) container.lookup(CompilerManager.ROLE);

            assertThatExceptionOfType(NoSuchCompilerException.class)
                    .isThrownBy(() -> manager.getCompiler("missing-compiler"))
                    .withMessage("No such compiler 'missing-compiler'.")
                    .satisfies(exception -> assertThat(exception.getCompilerId())
                            .isEqualTo("missing-compiler"));
        } finally {
            container.dispose();
        }
    }
}
