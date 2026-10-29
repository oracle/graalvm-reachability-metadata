/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.xml.transform.sax.SAXSource;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.XMLCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

public class XMLCatalogInnerExternalResolverTest {
    @TempDir
    File directory;

    @Test
    void resolvesSystemAndUriEntriesFromExternalCatalog() throws Exception {
        File payload = new File(directory, "payload.xml");
        Files.writeString(payload.toPath(), "<payload>resolved</payload>");
        File catalogFile = new File(directory, "catalog.xml");
        Files.writeString(catalogFile.toPath(), """
                <catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">
                  <system systemId="urn:ant:entity" uri="payload.xml"/>
                  <uri name="urn:ant:document" uri="payload.xml"/>
                </catalog>
                """);
        Project project = TestSupport.project(directory);
        XMLCatalog catalog = new XMLCatalog();
        catalog.setProject(project);
        catalog.createCatalogPath().setLocation(catalogFile);
        InputSource entity = catalog.resolveEntity(null, "urn:ant:entity");
        assertNotNull(entity);
        assertEquals("<payload>resolved</payload>", read(entity));
        SAXSource source = (SAXSource) catalog.resolve("urn:ant:document", null);
        assertNotNull(source);
        assertEquals("<payload>resolved</payload>", read(source.getInputSource()));
    }

    private static String read(InputSource source) throws Exception {
        InputStream bytes = source.getByteStream();
        if (bytes == null) {
            bytes = new URI(source.getSystemId()).toURL().openStream();
        }
        try (InputStream input = bytes) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
