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
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.XMLCatalog;
import org.apache.tools.ant.types.resolver.ApacheCatalog;
import org.apache.tools.ant.types.resolver.ApacheCatalogResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

public class ApacheCatalogResolverTest {
    private static final String PUBLIC_ID = "-//Example//DTD Apache Resolver Entry//EN";
    private static final String URI = "http://example.test/apache-resolver-entry.xml";

    @TempDir
    Path temporaryDirectory;

    @Test
    void parseCatalogAddsPublicAndUriEntriesToXmlCatalog() throws Exception {
        XMLCatalog xmlCatalog = newXmlCatalog();
        Path dtdResource = writeResource("apache-resolver-entry.dtd", "<!ELEMENT example EMPTY>");
        Path uriResource = writeResource("apache-resolver-entry.xml", "<example/>");
        Path catalogFile = writeXmlCatalog(dtdResource.getFileName(), uriResource.getFileName());
        ApacheCatalogResolver resolver = new ApacheCatalogResolver();
        resolver.setXMLCatalog(xmlCatalog);

        resolver.parseCatalog(catalogFile.toString());

        assertThat(System.getProperty("xml.catalog.className"))
                .isEqualTo(ApacheCatalog.class.getName());
        InputSource publicSource = xmlCatalog.resolveEntity(PUBLIC_ID, "missing.dtd");
        assertThat(publicSource).isNotNull();
        assertThat(publicSource.getSystemId()).endsWith(dtdResource.getFileName().toString());
        Source uriSource = xmlCatalog.resolve(URI, temporaryDirectory.toUri().toString());
        assertThat(uriSource).isInstanceOf(SAXSource.class);
        InputSource uriInputSource = ((SAXSource) uriSource).getInputSource();
        assertThat(uriInputSource).isNotNull();
        assertThat(uriInputSource.getSystemId()).endsWith(uriResource.getFileName().toString());
    }

    private XMLCatalog newXmlCatalog() {
        Project project = new Project();
        project.setBaseDir(temporaryDirectory.toFile());

        XMLCatalog xmlCatalog = new XMLCatalog();
        xmlCatalog.setProject(project);
        return xmlCatalog;
    }

    private Path writeResource(String fileName, String content) throws IOException {
        return Files.writeString(
                temporaryDirectory.resolve(fileName),
                content,
                StandardCharsets.UTF_8);
    }

    private Path writeXmlCatalog(Path dtdResourceName, Path uriResourceName) throws IOException {
        String catalogXml = """
                <catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">
                    <public publicId="%s" uri="%s"/>
                    <uri name="%s" uri="%s"/>
                </catalog>
                """.formatted(PUBLIC_ID, dtdResourceName, URI, uriResourceName);
        return Files.writeString(
                temporaryDirectory.resolve("apache-catalog.xml"),
                catalogXml,
                StandardCharsets.UTF_8);
    }
}
