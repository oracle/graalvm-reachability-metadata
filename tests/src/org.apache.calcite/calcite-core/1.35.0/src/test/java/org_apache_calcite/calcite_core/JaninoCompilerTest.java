/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.apache.calcite.util.javac.JavaCompilerArgs;
import org.apache.calcite.util.javac.JaninoCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JaninoCompilerTest {
    @TempDir
    Path outputDirectory;

    @Test
    void compilesInMemoryJavaSourceWithJaninoClassLoader() {
        JaninoCompiler compiler = new JaninoCompiler();
        JavaCompilerArgs args = compiler.getArgs();
        String fullClassName = "JaninoGeneratedGreeter";
        String source = """
                public class JaninoGeneratedGreeter {
                    public String greet(String name) {
                        return "hello " + name;
                    }
                }
                """;

        args.setDestdir(outputDirectory.toString());
        args.setFullClassName(fullClassName);
        args.setSource(source, "JaninoGeneratedGreeter.java");
        compiler.compile();

        assertThat(args.supportsSetSource()).isTrue();
        assertThat(args.getFileNames()).containsExactly("JaninoGeneratedGreeter.java");
        assertThat(compiler.getClassLoader()).isNotNull();
        assertThat(compiler.getTotalByteCodeSize()).isPositive();
    }
}
