/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileNotFoundException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import org.apache.jasper.JasperException;
import org.apache.jasper.compiler.AntCompiler;
import org.apache.jasper.compiler.Compiler;
import org.junit.jupiter.api.Test;

public class CompilerTest {
    @Test
    void constructorResolvesLoggerClassLiteralThroughGeneratedHelper() throws Throwable {
        final VarHandle cachedClassLiteral = cachedClassLiteralHandle();
        cachedClassLiteral.set(null);
        assertThat(cachedClassLiteral.get()).isNull();

        final Compiler compiler = new NoClassGenerationCompiler();

        assertThat(compiler.getPageNodes()).isNull();
        assertThat(cachedClassLiteral.get()).isSameAs(Compiler.class);
    }

    @Test
    void publicCompilerImplementationResolvesLoggerClassLiteralThroughBaseConstructor() throws Exception {
        final VarHandle cachedClassLiteral = cachedClassLiteralHandle();
        cachedClassLiteral.set(null);
        assertThat(cachedClassLiteral.get()).isNull();

        final Compiler compiler = new AntCompiler();

        assertThat(compiler).isInstanceOf(AntCompiler.class);
        assertThat(cachedClassLiteral.get()).isSameAs(Compiler.class);
    }

    @Test
    void generatedClassLiteralHelperResolvesCompilerClassByName() throws Throwable {
        final MethodHandle classLiteralHelper = MethodHandles.privateLookupIn(
                Compiler.class,
                MethodHandles.lookup()).findStatic(
                        Compiler.class,
                        "class$",
                        MethodType.methodType(Class.class, String.class));

        final Class<?> resolvedClass = (Class<?>) classLiteralHelper.invokeExact(
                "org.apache.jasper.compiler.Compiler");

        assertThat(resolvedClass).isSameAs(Compiler.class);
    }

    private static VarHandle cachedClassLiteralHandle() throws Exception {
        final String[] fieldNameParts = new String[] {
                "class$org$apache$jasper$compiler$",
                "Compiler"
        };
        return MethodHandles.privateLookupIn(
                Compiler.class,
                MethodHandles.lookup()).findStaticVarHandle(
                        Compiler.class,
                        fieldNameParts[0] + fieldNameParts[1],
                        Class.class);
    }

    private static final class NoClassGenerationCompiler extends Compiler {
        @Override
        protected void generateClass(String[] smap) throws FileNotFoundException, JasperException, Exception {
            // This test only needs construction to initialize Compiler's logger.
        }
    }
}
