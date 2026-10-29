/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Reference;
import org.apache.tools.ant.types.ResourceLocation;
import org.apache.tools.ant.types.XMLCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

public class XMLCatalogTest {
    @TempDir
    File directory;

    @Test
    void resolvesPublicIdentifierThroughCatalogReference() throws Exception {
        File dtd = new File(directory, "local.dtd");
        Files.writeString(dtd.toPath(), "<!ELEMENT message (#PCDATA)>");
        Project project = TestSupport.project(directory);
        XMLCatalog original = new XMLCatalog();
        original.setProject(project);
        ResourceLocation entry = new ResourceLocation();
        entry.setPublicId("-//ANT TEST//DTD MESSAGE//EN");
        entry.setLocation(dtd.getAbsolutePath());
        original.addDTD(entry);
        project.addReference("catalog", original);
        XMLCatalog reference = new XMLCatalog();
        reference.setProject(project);
        reference.setRefid(new Reference("catalog"));
        InputSource result = reference.resolveEntity("-//ANT TEST//DTD MESSAGE//EN", null);
        try (InputStream input = result.getByteStream()) {
            assertEquals("<!ELEMENT message (#PCDATA)>", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
