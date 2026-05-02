/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.XSLTLiaison;
import org.apache.tools.ant.taskdefs.XSLTProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class XSLTProcessTest {
    private static final String TRAX_LIAISON =
            "org.apache.tools.ant.taskdefs.optional.TraXLiaison";
    private static final String XSLP_LIAISON =
            "org.apache.tools.ant.taskdefs.optional.XslpLiaison";
    private static final String XALAN_LIAISON =
            "org.apache.tools.ant.taskdefs.optional.XalanLiaison";

    @TempDir
    Path temporaryDirectory;

    @Test
    void executesTraxProcessorLoadedByDefaultName() throws IOException {
        Path inputFile = writeFile("input.xml", """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <document><title>Ant XSLT</title></document>
                """);
        Path stylesheetFile = writeFile("style.xsl", """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">
                    <xsl:output method=\"text\"/>
                    <xsl:template match=\"/document\">
                        <xsl:value-of select=\"title\"/>
                    </xsl:template>
                </xsl:stylesheet>
                """);
        Path outputFile = temporaryDirectory.resolve("result.txt");
        ExposedXSLTProcess task = newTask("trax");
        task.setStyle(stylesheetFile.toString());
        task.setIn(inputFile.toFile());
        task.setOut(outputFile.toFile());

        task.execute();

        assertThat(Files.readString(outputFile, StandardCharsets.UTF_8)).contains("Ant XSLT");
    }

    @Test
    void resolvesDeprecatedXslpProcessorByName() {
        assertOptionalProcessorResolution("xslp", XSLP_LIAISON);
    }

    @Test
    void resolvesDeprecatedXalanProcessorByName() {
        assertOptionalProcessorResolution("xalan", XALAN_LIAISON);
    }

    @Test
    void resolvesCustomProcessorByClassName() {
        ExposedXSLTProcess task = newTask(TRAX_LIAISON);

        XSLTLiaison liaison = task.exposeLiaison();

        assertThat(liaison.getClass().getName()).isEqualTo(TRAX_LIAISON);
    }

    @Test
    void resolvesCustomProcessorThroughConfiguredAntClasspath() {
        ExposedXSLTProcess task = newTask(TRAX_LIAISON);
        task.createClasspath();

        XSLTLiaison liaison = task.exposeLiaison();

        assertThat(liaison.getClass().getName()).isEqualTo(TRAX_LIAISON);
    }

    private void assertOptionalProcessorResolution(String processor, String expectedClassName) {
        ExposedXSLTProcess task = newTask(processor);

        XSLTLiaison liaison = task.exposeLiaison();

        assertThat(liaison.getClass().getName()).isEqualTo(expectedClassName);
    }

    private ExposedXSLTProcess newTask(String processor) {
        Project project = new Project();
        project.setBaseDir(temporaryDirectory.toFile());
        ExposedXSLTProcess task = new ExposedXSLTProcess();
        task.setProject(project);
        task.setProcessor(processor);
        return task;
    }

    private Path writeFile(String fileName, String content) throws IOException {
        Path file = temporaryDirectory.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static final class ExposedXSLTProcess extends XSLTProcess {
        XSLTLiaison exposeLiaison() {
            return getLiaison();
        }
    }
}
