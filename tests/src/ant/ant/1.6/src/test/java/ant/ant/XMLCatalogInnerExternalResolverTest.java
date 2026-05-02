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
import org.apache.tools.ant.types.Path.PathElement;
import org.apache.tools.ant.types.ResourceLocation;
import org.apache.tools.ant.types.XMLCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

public class XMLCatalogInnerExternalResolverTest {
    private static final String CATALOG_PUBLIC_ID = "-//Example//DTD Catalog Entry//EN";
    private static final String INLINE_PUBLIC_ID = "-//Example//DTD Missing Inline Entry//EN";
    private static final String INLINE_URI = "http://example.test/missing-inline-uri.xml";
    private static final String UNMATCHED_PUBLIC_ID = "-//Example//DTD Unmatched//EN";
    private static final String UNMATCHED_URI = "http://example.test/unmatched-uri.xml";

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesExternalCatalogPathEntriesThroughApacheResolver() throws Exception {
        XMLCatalog catalog = newCatalog();
        Path resource = Files.writeString(
                temporaryDirectory.resolve("catalog-entry.dtd"),
                "<!ELEMENT example EMPTY>",
                StandardCharsets.UTF_8);
        Path catalogFile = writeXmlCatalog(resource.getFileName().toString());
        PathElement pathElement = catalog.createCatalogPath().createPathElement();
        pathElement.setLocation(catalogFile.toFile());

        InputSource source = catalog.resolveEntity(CATALOG_PUBLIC_ID, "missing.dtd");

        assertThat(source).isNotNull();
        assertThat(source.getSystemId()).endsWith(resource.getFileName().toString());
    }

    @Test
    void delegatesEntityResolutionToApacheResolverForUnreadableInlineAndUnmatchedEntries()
            throws Exception {
        XMLCatalog catalog = newCatalog();
        catalog.addDTD(resourceLocation(INLINE_PUBLIC_ID, "missing-inline.dtd"));

        InputSource inlineSource = catalog.resolveEntity(INLINE_PUBLIC_ID, "missing-inline.dtd");
        InputSource unmatchedSource = catalog.resolveEntity(UNMATCHED_PUBLIC_ID, "unmatched.dtd");

        assertThat(inlineSource).isNull();
        assertThat(unmatchedSource).isNull();
    }

    @Test
    void delegatesUriResolutionToApacheResolverForUnreadableInlineAndUnmatchedEntries()
            throws Exception {
        XMLCatalog catalog = newCatalog();
        catalog.addEntity(resourceLocation(INLINE_URI, "missing-inline.xml"));

        Source inlineSource = catalog.resolve(INLINE_URI, temporaryDirectory.toUri().toString());
        Source unmatchedSource = catalog.resolve(
                UNMATCHED_URI,
                temporaryDirectory.toUri().toString());

        assertThat(inlineSource).isInstanceOf(SAXSource.class);
        assertThat(((SAXSource) inlineSource).getInputSource()).isNotNull();
        assertThat(unmatchedSource).isInstanceOf(SAXSource.class);
        assertThat(((SAXSource) unmatchedSource).getInputSource()).isNotNull();
    }

    private XMLCatalog newCatalog() {
        Project project = new Project();
        project.setBaseDir(temporaryDirectory.toFile());

        XMLCatalog catalog = new XMLCatalog();
        catalog.setProject(project);
        return catalog;
    }

    private ResourceLocation resourceLocation(String publicId, String location) {
        ResourceLocation resourceLocation = new ResourceLocation();
        resourceLocation.setPublicId(publicId);
        resourceLocation.setLocation(location);
        return resourceLocation;
    }

    private Path writeXmlCatalog(String resourceName) throws IOException {
        String catalogXml = """
                <catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">
                    <public publicId="%s" uri="%s"/>
                </catalog>
                """.formatted(CATALOG_PUBLIC_ID, resourceName);
        Path catalogFile = temporaryDirectory.resolve("catalog.xml");
        return Files.writeString(catalogFile, catalogXml, StandardCharsets.UTF_8);
    }
}
