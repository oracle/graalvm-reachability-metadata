/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.file.Path;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Rmic;
import org.apache.tools.ant.taskdefs.rmic.SunRmic;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sun.rmi.rmic.Main;

import static org.assertj.core.api.Assertions.assertThat;

public class SunRmicTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void executeLoadsSunRmicImplementationAndRunsIt() throws Throwable {
        Main.reset();
        clearCompilerGeneratedClassCache();
        SunRmic adapter = new SunRmic();
        adapter.setRmic(newRmic());

        try {
            boolean completed = adapter.execute();

            assertThat(completed).isTrue();
            assertThat(Main.invocationCount()).isEqualTo(1);
            assertThat(Main.lastCompilerName()).isEqualTo("rmic");
            assertThat(Main.lastArguments())
                    .contains("-d", temporaryDirectory.toString(), "-classpath");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void compilerGeneratedClassLiteralHelperResolvesConstructorParameterTypes() throws Throwable {
        Class<?> parameterType = resolveCompilerGeneratedClassLiteral(OutputStream.class.getName());

        assertThat(parameterType).isSameAs(OutputStream.class);
    }

    private static Class<?> resolveCompilerGeneratedClassLiteral(String className) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                SunRmic.class,
                MethodHandles.lookup());
        MethodHandle handle = lookup.findStatic(
                SunRmic.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) handle.invoke(className);
    }

    private static void clearCompilerGeneratedClassCache()
            throws IllegalAccessException, NoSuchFieldException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                SunRmic.class,
                MethodHandles.lookup());
        clearClassCache(lookup, "class$java$io$OutputStream");
        clearClassCache(lookup, "class$java$lang$String");
        clearClassCache(lookup, "array$Ljava$lang$String");
    }

    private static void clearClassCache(MethodHandles.Lookup lookup, String fieldName)
            throws IllegalAccessException, NoSuchFieldException {
        VarHandle handle = lookup.findStaticVarHandle(SunRmic.class, fieldName, Class.class);
        handle.set(null);
    }

    private Rmic newRmic() {
        Project project = new Project();
        project.init();

        Rmic rmic = new Rmic();
        rmic.setProject(project);
        rmic.setTaskName("rmic");
        rmic.setBase(temporaryDirectory.toFile());
        return rmic;
    }
}
