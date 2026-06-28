/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.janino;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.codehaus.commons.compiler.ICompiler;
import org.codehaus.janino.CompilerFactory;
import org.junit.jupiter.api.Test;

public class AbstractCompilerTest {
    @Test
    void newCompilerInitializesIClassLoaderFromRuntimeClassPath() throws Exception {
        try {
            final CompilerFactory compilerFactory = new CompilerFactory();
            final ICompiler compiler = compilerFactory.newCompiler();

            compiler.setClassPath(new File[0]);

            assertThat(compiler).isNotNull();
        } catch (Throwable throwable) {
            NativeImageDynamicClassLoadingSupport.rethrowIfNotNativeImageDynamicClassLoadingFailure(throwable);
        }
    }
}
