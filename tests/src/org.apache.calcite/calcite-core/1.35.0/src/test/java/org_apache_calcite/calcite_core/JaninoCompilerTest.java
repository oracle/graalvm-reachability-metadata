/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.util.javac.JaninoCompiler;
import org.apache.calcite.util.javac.JavaCompilerArgs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class JaninoCompilerTest {
    @Test
    void compilesInMemorySourceAndPersistsBytecode(@TempDir Path classesDir) {
        JaninoCompiler compiler = new JaninoCompiler();
        JavaCompilerArgs args = compiler.getArgs();
        String className = "JaninoCompilerGeneratedHello";

        args.setDestdir(classesDir.toString());
        args.setFullClassName(className);
        args.setSource("""
            public class JaninoCompilerGeneratedHello {
              public int answer() {
                return 40 + 2;
              }
            }
            """, className + ".java");

        compiler.compile();

        assertThat(args.supportsSetSource()).isTrue();
        assertThat(compiler.getTotalByteCodeSize()).isGreaterThan(0);
        assertThat(classesDir.resolve(className + ".class")).exists().isRegularFile();
        assertThat(compiler.getClassLoader()).isInstanceOf(ClassLoader.class);
    }
}
