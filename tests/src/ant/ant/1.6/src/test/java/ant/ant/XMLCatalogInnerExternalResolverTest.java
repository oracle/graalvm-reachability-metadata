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
import org.apache.tools.ant.types.Path.PathElement;
import org.apache.tools.ant.types.ResourceLocation;
import org.apache.tools.ant.types.XMLCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

public class XMLCatalogInnerExternalResolverTest {
    private static final String EXTERNAL_PUBLIC_ID = "-//Example//DTD External Catalog//EN";
    private static final String INLINE_PUBLIC_ID = "-//Example//DTD Missing Inline Catalog//EN";
    private static final String UNMATCHED_PUBLIC_ID = "-//Example//DTD Unmatched Catalog//EN";
    private static final String INLINE_URI = "https://example.test/missing-inline-catalog.xml";
    private static final String UNMATCHED_URI = "https://example.test/unmatched-catalog.xml";

    @Test
    void resolvesEntriesParsedFromAnExternalCatalog(@TempDir Path temporaryDirectory) throws Exception {
        Path dtdFile = Files.writeString(
                temporaryDirectory.resolve("external-catalog.dtd"),
                "<!ELEMENT example EMPTY>",
                StandardCharsets.UTF_8);
        Path catalogFile = Files.writeString(
                temporaryDirectory.resolve("external-catalog.cat"),
                "PUBLIC \"" + EXTERNAL_PUBLIC_ID + "\" \"" + dtdFile.getFileName() + "\"\n",
                StandardCharsets.UTF_8);
        XMLCatalog catalog = newCatalog(temporaryDirectory);
        PathElement catalogPath = catalog.createCatalogPath().createPathElement();
        catalogPath.setLocation(catalogFile.toFile());

        InputSource source = catalog.resolveEntity(EXTERNAL_PUBLIC_ID, "missing.dtd");

        assertThat(source).isNotNull();
        try (InputStream stream = source.getByteStream()) {
            assertThat(stream).isNotNull();
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).contains("example");
        }
    }

    @Test
    void delegatesUnresolvableEntityEntriesToTheExternalCatalogResolver(@TempDir Path temporaryDirectory)
            throws Exception {
        XMLCatalog catalog = newCatalog(temporaryDirectory);
        catalog.addDTD(resourceLocation(INLINE_PUBLIC_ID, "missing-inline.dtd"));

        InputSource inlineSource = catalog.resolveEntity(INLINE_PUBLIC_ID, "missing-inline.dtd");
        InputSource unmatchedSource = catalog.resolveEntity(UNMATCHED_PUBLIC_ID, "unmatched.dtd");

        assertThat(inlineSource).isNull();
        assertThat(unmatchedSource).isNull();
    }

    @Test
    void delegatesUnresolvableUriEntriesToTheExternalCatalogResolver(@TempDir Path temporaryDirectory)
            throws Exception {
        XMLCatalog catalog = newCatalog(temporaryDirectory);
        catalog.addEntity(resourceLocation(INLINE_URI, "missing-inline.xml"));

        Source inlineSource = catalog.resolve(INLINE_URI, temporaryDirectory.toUri().toString());
        Source unmatchedSource = catalog.resolve(UNMATCHED_URI, temporaryDirectory.toUri().toString());

        assertThat(inlineSource).isInstanceOf(SAXSource.class);
        assertThat(((SAXSource) inlineSource).getInputSource()).isNotNull();
        assertThat(unmatchedSource).isInstanceOf(SAXSource.class);
        assertThat(((SAXSource) unmatchedSource).getInputSource()).isNotNull();
    }

    private static XMLCatalog newCatalog(Path baseDirectory) {
        Project project = new Project();
        project.setBaseDir(baseDirectory.toFile());

        XMLCatalog catalog = new XMLCatalog();
        catalog.setProject(project);
        return catalog;
    }

    private static ResourceLocation resourceLocation(String publicId, String location) {
        ResourceLocation resourceLocation = new ResourceLocation();
        resourceLocation.setPublicId(publicId);
        resourceLocation.setLocation(location);
        return resourceLocation;
    }
}
