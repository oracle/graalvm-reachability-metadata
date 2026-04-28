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
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.XMLCatalog;
import org.apache.tools.ant.types.resolver.ApacheCatalogResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

public class ApacheCatalogResolverTest {
    private static final String PUBLIC_ID = "-//Example//DTD Apache Resolver//EN";
    private static final String URI_ID = "urn:example:apache-resolver-document";

    @Test
    void parsesExternalCatalogEntriesIntoXmlCatalog(@TempDir Path temporaryDirectory) throws Exception {
        Path dtdFile = temporaryDirectory.resolve("apache-resolver.dtd");
        Path xmlFile = temporaryDirectory.resolve("apache-resolver.xml");
        Path catalogFile = temporaryDirectory.resolve("catalog.cat");
        Files.write(dtdFile, "<!ELEMENT example EMPTY>".getBytes(StandardCharsets.UTF_8));
        Files.write(xmlFile, "<example/>".getBytes(StandardCharsets.UTF_8));
        Files.write(catalogFile, catalog(dtdFile, xmlFile).getBytes(StandardCharsets.UTF_8));

        XMLCatalog catalog = newCatalog(temporaryDirectory);
        ApacheCatalogResolver resolver = new ApacheCatalogResolver();
        resolver.setXMLCatalog(catalog);

        resolver.parseCatalog(catalogFile.toString());

        InputSource entity = catalog.resolveEntity(PUBLIC_ID, "urn:example:missing-dtd");
        Source source = catalog.resolve(URI_ID, null);

        assertThat(entity).isNotNull();
        assertThat(entity.getSystemId()).endsWith("apache-resolver.dtd");
        try (InputStream byteStream = entity.getByteStream()) {
            assertThat(new String(byteStream.readAllBytes(), StandardCharsets.UTF_8)).contains("example");
        }
        assertThat(source).isInstanceOf(SAXSource.class);
        InputSource inputSource = ((SAXSource) source).getInputSource();
        assertThat(inputSource.getSystemId()).endsWith("apache-resolver.xml");
    }

    private static XMLCatalog newCatalog(Path baseDirectory) {
        Project project = new Project();
        project.setBaseDir(baseDirectory.toFile());

        XMLCatalog catalog = new XMLCatalog();
        catalog.setProject(project);
        return catalog;
    }

    private static String catalog(Path dtdFile, Path xmlFile) {
        return "PUBLIC \"" + PUBLIC_ID + "\" \"" + dtdFile.getFileName() + "\"\n"
                + "URI \"" + URI_ID + "\" \"" + xmlFile.getFileName() + "\"\n";
    }
}
