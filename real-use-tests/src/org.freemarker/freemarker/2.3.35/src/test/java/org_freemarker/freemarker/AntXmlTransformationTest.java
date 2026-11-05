/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_freemarker.freemarker;

import freemarker.ext.ant.FreemarkerXmlTask;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises FreeMarker's Ant XML transformation subsystem to build a catalog page.
public class AntXmlTransformationTest {
    @Test
    @Timeout(60)
    public void transformsCatalogXmlThroughAntTask(@TempDir Path temporaryDirectory) throws Exception {
        Path inputDirectory = Files.createDirectory(temporaryDirectory.resolve("input"));
        Path templateDirectory = Files.createDirectory(temporaryDirectory.resolve("templates"));
        Path outputDirectory = Files.createDirectory(temporaryDirectory.resolve("output"));

        Files.writeString(inputDirectory.resolve("catalog.xml"), "<catalog>"
                + "<product sku=\"cable\"><name>USB-C cable</name>"
                + "<price>9.50</price></product>"
                + "<product sku=\"dock\"><name>Dock</name>"
                + "<price>79.00</price></product>"
                + "</catalog>", StandardCharsets.UTF_8);
        Files.writeString(templateDirectory.resolve("catalog.ftl"),
                "<#list document[\"*\"].product as product>"
                        + "${product[\"@sku\"]._text}: ${product.name._text} - ${product.price._text}\n"
                        + "</#list>", StandardCharsets.UTF_8);

        Project project = new Project();
        project.setBaseDir(inputDirectory.toFile());
        project.init();

        FreemarkerXmlTask task = new FreemarkerXmlTask();
        task.setProject(project);
        task.setBasedir(inputDirectory.toFile());
        task.setDestdir(outputDirectory.toFile());
        task.setTemplateDir(templateDirectory.toFile());
        task.setTemplate("catalog.ftl");
        task.setExtension(".html");
        task.execute();

        assertEquals("cable: USB-C cable - 9.50\n"
                        + "dock: Dock - 79.00\n",
                Files.readString(outputDirectory.resolve("catalog.html"), StandardCharsets.UTF_8));
    }
}
