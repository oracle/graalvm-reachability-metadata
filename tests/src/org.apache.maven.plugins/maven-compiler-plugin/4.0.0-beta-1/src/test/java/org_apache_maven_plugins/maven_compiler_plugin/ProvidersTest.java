/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_compiler_plugin;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.testing.MavenDITest;
import org.codehaus.plexus.compiler.Compiler;
import org.codehaus.plexus.compiler.javac.JavacCompiler;
import org.junit.jupiter.api.Test;

@MavenDITest
public class ProvidersTest {
    @Inject
    @Named("javac")
    private Compiler compiler;

    @Test
    public void mavenDiProvidesTheJavacCompiler() {
        assertNotNull(compiler);
        assertTrue(compiler instanceof JavacCompiler);
    }
}
