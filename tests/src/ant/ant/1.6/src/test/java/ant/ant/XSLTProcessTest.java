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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.XSLTLiaison;
import org.apache.tools.ant.taskdefs.XSLTProcess;
import org.apache.tools.ant.taskdefs.optional.TraXLiaison;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class XSLTProcessTest {
    @Test
    void loadsAConfiguredLiaisonFromTheTaskClasspath(@TempDir Path temporaryDirectory) throws IOException {
        Path input = Files.writeString(temporaryDirectory.resolve("input.xml"), "Ant");
        Path stylesheet = Files.writeString(temporaryDirectory.resolve("stylesheet.xsl"), "unused");
        Path output = temporaryDirectory.resolve("output.xml");

        XSLTProcess process = newProcess(temporaryDirectory);
        process.setProcessor(CopyingLiaison.class.getName());
        process.createClasspath();
        process.setStyle(stylesheet.toString());
        process.setIn(input.toFile());
        process.setOut(output.toFile());
        process.execute();

        assertThat(Files.readString(output)).isEqualTo("transformed: Ant");
    }

    @Test
    void loadsAConfiguredLiaisonWithoutTaskClasspath(@TempDir Path temporaryDirectory) throws IOException {
        Path input = Files.writeString(temporaryDirectory.resolve("input.xml"), "Ant");
        Path stylesheet = Files.writeString(temporaryDirectory.resolve("stylesheet.xsl"), "unused");
        Path output = temporaryDirectory.resolve("output.xml");

        XSLTProcess process = newProcess(temporaryDirectory);
        process.setProcessor(CopyingLiaison.class.getName());
        process.setStyle(stylesheet.toString());
        process.setIn(input.toFile());
        process.setOut(output.toFile());
        process.execute();

        assertThat(Files.readString(output)).isEqualTo("transformed: Ant");
    }

    @Test
    void resolvesTheTraxProcessor(@TempDir Path temporaryDirectory) {
        ExposedXSLTProcess process = newProcess(temporaryDirectory);
        process.setProcessor("trax");

        assertThat(process.resolveLiaison()).isInstanceOf(TraXLiaison.class);
    }

    private static ExposedXSLTProcess newProcess(Path baseDirectory) {
        Project project = new Project();
        project.setBaseDir(baseDirectory.toFile());
        project.init();

        ExposedXSLTProcess process = new ExposedXSLTProcess();
        process.setProject(project);
        process.init();
        return process;
    }

    public static final class CopyingLiaison implements XSLTLiaison {
        @Override
        public void setStylesheet(File stylesheet) {
        }

        @Override
        public void transform(File input, File output) throws IOException {
            Files.writeString(
                    output.toPath(), "transformed: " + Files.readString(input.toPath()), StandardCharsets.UTF_8);
        }

        @Override
        public void addParam(String name, String expression) {
        }
    }

    public static final class ExposedXSLTProcess extends XSLTProcess {
        public XSLTLiaison resolveLiaison() {
            return getLiaison();
        }
    }
}
