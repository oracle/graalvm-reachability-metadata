/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.FileNotFoundException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.file.Path;

import org.apache.jasper.compiler.SmapUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SmapUtilTest {
    @Test
    void constructorInitializesUtilityLogger() throws Throwable {
        final VarHandle cachedClassLiteral = cachedClassLiteralHandle();
        cachedClassLiteral.set(null);
        assertThat(cachedClassLiteral.get()).isNull();

        final SmapUtil smapUtil = new SmapUtil();

        assertThat(smapUtil).isNotNull();
        assertThat(cachedClassLiteral.get()).isSameAs(SmapUtil.class);
    }

    @Test
    void generatedClassLiteralHelperResolvesNamedClass() throws Throwable {
        final MethodHandle classLiteralHelper = MethodHandles.privateLookupIn(
                SmapUtil.class,
                MethodHandles.lookup()).findStatic(
                        SmapUtil.class,
                        "class$",
                        MethodType.methodType(Class.class, String.class));

        final String targetClassName = "org.apache.commons.logging.impl.SimpleLog";
        final Class<?> resolvedClass = (Class<?>) classLiteralHelper.invokeExact(targetClassName);

        assertThat(resolvedClass.getName()).isEqualTo(targetClassName);
    }

    @Test
    void installSmapInitializesInstallerBeforeReadingClassFile(@TempDir Path temporaryDirectory) {
        final Path missingClassFile = temporaryDirectory.resolve("MissingServlet.class");
        final String[] smap = {
                missingClassFile.toString(),
                "SMAP\nMissingServlet.java\nJSP\n*S JSP\n*F\n*E\n"
        };

        assertThatThrownBy(() -> SmapUtil.installSmap(smap))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("MissingServlet.class");
    }

    private static VarHandle cachedClassLiteralHandle() throws Exception {
        final String[] fieldNameParts = new String[] {
                "class$org$apache$jasper$compiler$",
                "SmapUtil"
        };
        return MethodHandles.privateLookupIn(
                SmapUtil.class,
                MethodHandles.lookup()).findStaticVarHandle(
                        SmapUtil.class,
                        fieldNameParts[0] + fieldNameParts[1],
                        Class.class);
    }
}
