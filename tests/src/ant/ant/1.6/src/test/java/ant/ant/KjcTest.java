/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import at.dms.kjc.Main;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.taskdefs.compilers.Kjc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class KjcTest {
    @BeforeEach
    void resetKjcCompiler() throws ReflectiveOperationException {
        Main.reset();
        clearClassLiteralCache();
    }

    @Test
    void invokesKopiCompilerAdapterThroughJavacTask(@TempDir Path temporaryDirectory) throws IOException {
        Path sourceDirectory = temporaryDirectory.resolve("src");
        Path destinationDirectory = temporaryDirectory.resolve("classes");
        Files.createDirectories(sourceDirectory);
        Files.createDirectories(destinationDirectory);
        Path sourceFile = sourceDirectory.resolve("KopiGreeting.java");
        Files.write(sourceFile, kopiGreetingSource().getBytes(StandardCharsets.UTF_8));

        Javac javac = newKopiJavacTask(sourceDirectory, destinationDirectory);

        javac.execute();

        String[] lastArguments = Main.getLastArguments();
        assertThat(lastArguments).isNotNull();
        List<String> arguments = Arrays.asList(lastArguments);
        assertThat(arguments)
            .contains(
                "-d",
                destinationDirectory.toFile().getAbsolutePath(),
                "-classpath",
                sourceFile.toFile().getAbsolutePath());
        assertThat(arguments.get(arguments.indexOf("-classpath") + 1))
            .contains(destinationDirectory.toFile().getAbsolutePath())
            .contains(sourceDirectory.toFile().getAbsolutePath());
    }

    private static void clearClassLiteralCache() throws ReflectiveOperationException {
        Field field = Kjc.class.getDeclaredField("array$Ljava$lang$String");
        field.setAccessible(true);
        field.set(null, null);
    }

    private static Javac newKopiJavacTask(Path sourceDirectory, Path destinationDirectory) {
        Project project = new Project();
        project.init();
        project.setBaseDir(sourceDirectory.getParent().toFile());

        Javac javac = new Javac();
        javac.setProject(project);
        javac.setTaskName("javac");
        javac.setCompiler("kjc");
        javac.setSrcdir(new org.apache.tools.ant.types.Path(project, sourceDirectory.toString()));
        javac.setDestdir(destinationDirectory.toFile());
        javac.setIncludeantruntime(false);
        javac.setIncludejavaruntime(false);
        return javac;
    }

    private static String kopiGreetingSource() {
        return "public class KopiGreeting {\n"
            + "    public String message() {\n"
            + "        return \"hello\";\n"
            + "    }\n"
            + "}\n";
    }
}
