/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.taskdefs.compilers.Kjc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import at.dms.kjc.Main;

import static org.assertj.core.api.Assertions.assertThat;

public class KjcTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void executeResolvesCompilerGeneratedClassLiteralForCompileSignature() throws Exception {
        Main.reset();
        clearCompilerGeneratedClassCache();
        Kjc compiler = new Kjc();
        compiler.setJavac(newJavacTask());

        boolean compilationSucceeded = compiler.execute();

        assertThat(compilationSucceeded).isTrue();
        assertThat(Main.invocationCount()).isEqualTo(1);
        assertThat(Main.lastArguments())
                .contains("-d", temporaryDirectory.resolve("classes").toString())
                .contains("-classpath")
                .anySatisfy(argument -> assertThat(argument)
                        .contains(temporaryDirectory.resolve("sources").toString()));
    }

    private Javac newJavacTask() throws IOException {
        Path sourceDirectory = temporaryDirectory.resolve("sources");
        Path destinationDirectory = temporaryDirectory.resolve("classes");
        Files.createDirectories(sourceDirectory);
        Files.createDirectories(destinationDirectory);
        Files.writeString(sourceDirectory.resolve("Example.java"), "class Example { }\n");

        Project project = new Project();
        project.setBaseDir(temporaryDirectory.toFile());
        project.init();

        Javac javac = new Javac();
        javac.setProject(project);
        javac.setTaskName("javac");
        javac.createSrc().setLocation(sourceDirectory.toFile());
        javac.setDestdir(destinationDirectory.toFile());
        javac.setIncludeantruntime(false);
        javac.setIncludejavaruntime(false);
        return javac;
    }

    private static void clearCompilerGeneratedClassCache()
            throws IllegalAccessException, NoSuchFieldException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                Kjc.class,
                MethodHandles.lookup());
        VarHandle handle = lookup.findStaticVarHandle(
                Kjc.class,
                "array$Ljava$lang$String",
                Class.class);
        handle.set(null);
    }
}
