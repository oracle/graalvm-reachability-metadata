/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Reference;
import org.apache.tools.ant.types.ResourceLocation;
import org.apache.tools.ant.types.XMLCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

public class XMLCatalogTest {
    private static final String PUBLIC_ID = "-//Example//DTD Referenced Catalog//EN";

    @Test
    void resolvesEntityThroughReferencedCatalog(@TempDir Path temporaryDirectory) throws Exception {
        Path dtdFile = temporaryDirectory.resolve("referenced-catalog.dtd");
        Files.write(dtdFile, "<!ELEMENT example EMPTY>".getBytes(StandardCharsets.UTF_8));

        Project project = new Project();
        project.init();
        project.setBaseDir(temporaryDirectory.toFile());

        XMLCatalog source = new XMLCatalog();
        source.setProject(project);
        source.addDTD(resourceLocation(PUBLIC_ID, dtdFile.getFileName().toString()));
        project.addReference("shared.xmlcatalog", source);

        XMLCatalog referenced = new XMLCatalog();
        referenced.setProject(project);
        referenced.setRefid(new Reference("shared.xmlcatalog"));

        InputSource inputSource = referenced.resolveEntity(
                PUBLIC_ID, "urn:example:referenced-catalog");

        assertThat(inputSource).isNotNull();
        assertThat(inputSource.getSystemId()).endsWith("referenced-catalog.dtd");
        try (InputStream byteStream = inputSource.getByteStream()) {
            assertThat(new String(byteStream.readAllBytes(), StandardCharsets.UTF_8)).contains("example");
        }
    }

    private static ResourceLocation resourceLocation(String publicId, String location) {
        ResourceLocation resourceLocation = new ResourceLocation();
        resourceLocation.setPublicId(publicId);
        resourceLocation.setLocation(location);
        return resourceLocation;
    }
}
