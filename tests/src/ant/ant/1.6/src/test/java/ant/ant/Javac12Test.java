/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.taskdefs.compilers.Javac12;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sun.tools.javac.Main;

public class Javac12Test {
    @BeforeEach
    void resetClassicCompiler() throws ReflectiveOperationException {
        Main.reset();
        resetLegacyClassLiteralCache();
    }

    @Test
    void invokesClassicCompilerThroughLegacyAdapter(@TempDir Path temporaryDirectory) throws IOException {
        Path sourceDirectory = temporaryDirectory.resolve("src");
        Path destinationDirectory = temporaryDirectory.resolve("classes");
        Files.createDirectories(sourceDirectory);
        Files.createDirectories(destinationDirectory);
        Path sourceFile = sourceDirectory.resolve("ClassicGreeting.java");
        Files.write(sourceFile, classicGreetingSource().getBytes(StandardCharsets.UTF_8));

        RecordingJavac javac = newClassicJavacTask(
            sourceDirectory,
            destinationDirectory,
            sourceFile.toFile());
        Javac12 adapter = new Javac12();
        adapter.setJavac(javac);

        assertThat(adapter.execute()).isTrue();
        assertThat(Main.getLastProgramName()).isEqualTo("javac");
        assertThat(Arrays.asList(Main.getLastArguments()))
            .contains(
                "-d",
                destinationDirectory.toFile().getAbsolutePath(),
                sourceFile.toFile().getAbsolutePath());
    }

    private static RecordingJavac newClassicJavacTask(
        Path sourceDirectory,
        Path destinationDirectory,
        File sourceFile) {
        Project project = new Project();
        project.init();
        project.setBaseDir(sourceDirectory.getParent().toFile());

        RecordingJavac javac = new RecordingJavac();
        javac.setProject(project);
        javac.setTaskName("javac");
        javac.setSrcdir(new org.apache.tools.ant.types.Path(project, sourceDirectory.toString()));
        javac.setDestdir(destinationDirectory.toFile());
        javac.setIncludeantruntime(false);
        javac.setIncludejavaruntime(false);
        javac.setCompileList(sourceFile);
        return javac;
    }

    private static void resetLegacyClassLiteralCache() throws ReflectiveOperationException {
        clearClassLiteralCache("class$java$io$OutputStream");
        clearClassLiteralCache("class$java$lang$String");
        clearClassLiteralCache("array$Ljava$lang$String");
    }

    private static void clearClassLiteralCache(String fieldName) throws ReflectiveOperationException {
        Field field = Javac12.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, null);
    }

    private static String classicGreetingSource() {
        return "public class ClassicGreeting {\n"
            + "    public String message() {\n"
            + "        return \"hello\";\n"
            + "    }\n"
            + "}\n";
    }

    private static final class RecordingJavac extends Javac {
        void setCompileList(File... files) {
            compileList = files;
        }
    }
}
