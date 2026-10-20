/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.common.compiler.Compiler;
import com.alibaba.dubbo.common.compiler.support.JavassistCompiler;
import org.junit.jupiter.api.Test;

public class AbstractCompilerTest {
    @Test
    void returnsAnAlreadyLoadedClassWithoutRedefiningIt() {
        Compiler compiler = new JavassistCompiler();
        String source =
                """
                package com.alibaba.dubbo.common.compiler.support;
                public class JavassistCompiler {
                }
                """;

        Class<?> compiledClass = compiler.compile(source, compiler.getClass().getClassLoader());

        assertThat(compiledClass).isSameAs(compiler.getClass());
    }
}
