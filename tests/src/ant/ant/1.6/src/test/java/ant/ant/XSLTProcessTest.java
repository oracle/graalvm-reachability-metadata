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
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.XSLTLiaison;
import org.apache.tools.ant.taskdefs.XSLTProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class XSLTProcessTest {
    @Test
    void resolvesDeprecatedOptionalProcessors(@TempDir Path temporaryDirectory) throws Exception {
        assertOptionalProcessorIsResolved("xslp", temporaryDirectory.resolve("xslp"));
        assertOptionalProcessorIsResolved("xalan", temporaryDirectory.resolve("xalan"));
    }

    @Test
    void resolvesCustomProcessorWithSystemClassLoader(@TempDir Path temporaryDirectory) throws Exception {
        Path inputFile = writeInputFile(temporaryDirectory);
        Path stylesheetFile = writeStylesheetFile(temporaryDirectory);
        Path outputFile = temporaryDirectory.resolve("custom-output.txt");

        XSLTProcess task = createTask(inputFile, outputFile, stylesheetFile);
        task.setProcessor(RecordingLiaison.class.getName());
        task.execute();

        assertThat(Files.readString(outputFile)).contains("recorded-source.xml-with-transform.xsl");
    }

    @Test
    void resolvesCustomProcessorWithAntClassLoader(@TempDir Path temporaryDirectory) throws Exception {
        Path inputFile = writeInputFile(temporaryDirectory);
        Path stylesheetFile = writeStylesheetFile(temporaryDirectory);
        Path outputFile = temporaryDirectory.resolve("custom-classpath-output.txt");

        XSLTProcess task = createTask(inputFile, outputFile, stylesheetFile);
        task.setProcessor(RecordingLiaison.class.getName());
        task.createClasspath();
        task.execute();

        assertThat(Files.readString(outputFile)).contains("recorded-source.xml-with-transform.xsl");
    }

    private static void assertOptionalProcessorIsResolved(String processor, Path temporaryDirectory) throws Exception {
        Files.createDirectories(temporaryDirectory);
        Path inputFile = writeInputFile(temporaryDirectory);
        Path stylesheetFile = writeStylesheetFile(temporaryDirectory);
        Path outputFile = temporaryDirectory.resolve(processor + "-output.txt");

        XSLTProcess task = createTask(inputFile, outputFile, stylesheetFile);
        task.setProcessor(processor);

        boolean completed = false;
        try {
            task.execute();
            completed = true;
        } catch (BuildException | LinkageError ex) {
            assertThat(ex).isInstanceOfAny(BuildException.class, LinkageError.class);
        }
        if (completed) {
            assertThat(outputFile).exists();
        }
    }

    private static XSLTProcess createTask(Path inputFile, Path outputFile, Path stylesheetFile) {
        Project project = new Project();
        project.init();

        XSLTProcess task = new XSLTProcess();
        task.setProject(project);
        task.init();
        task.setIn(inputFile.toFile());
        task.setOut(outputFile.toFile());
        task.setStyle(stylesheetFile.toString());
        task.setForce(true);
        return task;
    }

    private static Path writeInputFile(Path directory) throws IOException {
        Path inputFile = directory.resolve("source.xml");
        Files.writeString(inputFile, "<?xml version=\"1.0\"?><root><item>value</item></root>", StandardCharsets.UTF_8);
        return inputFile;
    }

    private static Path writeStylesheetFile(Path directory) throws IOException {
        Path stylesheetFile = directory.resolve("transform.xsl");
        String stylesheet = """
                <?xml version=\"1.0\"?>
                <xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">
                    <xsl:output method=\"text\"/>
                    <xsl:template match=\"/\">transformed-<xsl:value-of select=\"/root/item\"/></xsl:template>
                </xsl:stylesheet>
                """;
        Files.writeString(stylesheetFile, stylesheet, StandardCharsets.UTF_8);
        return stylesheetFile;
    }

    public static final class RecordingLiaison implements XSLTLiaison {
        private File stylesheet;

        public RecordingLiaison() {
        }

        @Override
        public void setStylesheet(File stylesheet) {
            this.stylesheet = stylesheet;
        }

        @Override
        public void addParam(String name, String expression) {
        }

        @Override
        public void transform(File infile, File outfile) throws IOException {
            String output = "recorded-" + infile.getName() + "-with-" + stylesheet.getName();
            Files.writeString(outfile.toPath(), output, StandardCharsets.UTF_8);
        }
    }
}
