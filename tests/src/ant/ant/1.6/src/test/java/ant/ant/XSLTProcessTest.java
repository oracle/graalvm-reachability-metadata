/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Files;

import org.apache.tools.ant.taskdefs.XSLTProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class XSLTProcessTest {
    @TempDir
    File directory;

    @Test
    void loadsTraxProcessorAndTransformsDocument() throws Exception {
        File input = new File(directory, "input.xml");
        File stylesheet = new File(directory, "style.xsl");
        File output = new File(directory, "output.txt");
        Files.writeString(input.toPath(), "<message>Ant transformation</message>");
        Files.writeString(stylesheet.toPath(), """
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:output method="text"/>
                  <xsl:template match="/"><xsl:value-of select="message"/></xsl:template>
                </xsl:stylesheet>
                """);
        XSLTProcess task = new XSLTProcess();
        task.setProject(TestSupport.project(directory));
        task.setIn(input);
        task.setOut(output);
        task.setStyle(stylesheet.getAbsolutePath());
        task.setProcessor("trax");
        task.execute();
        assertEquals("Ant transformation", Files.readString(output.toPath()));
        task.setProcessor("org.apache.tools.ant.taskdefs.optional.TraXLiaison");
        task.setForce(true);
        task.execute();
        assertEquals("Ant transformation", Files.readString(output.toPath()));
    }
}
