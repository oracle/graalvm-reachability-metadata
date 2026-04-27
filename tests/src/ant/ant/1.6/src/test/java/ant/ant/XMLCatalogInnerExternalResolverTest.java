/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.transform.Source;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.ResourceLocation;
import org.apache.tools.ant.types.XMLCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

public class XMLCatalogInnerExternalResolverTest {
    private static final String MATCHING_PUBLIC_ID = "-//Example//DTD Inline Missing//EN";
    private static final String UNMATCHED_PUBLIC_ID = "-//Example//DTD Unmatched Missing//EN";
    private static final String MATCHING_URI = "urn:example:inline-missing";
    private static final String UNMATCHED_URI = "urn:example:unmatched-missing";

    @Test
    void delegatesUnresolvedCatalogLookupsToTheExternalResolver(@TempDir Path temporaryDirectory) throws Exception {
        XMLCatalog catalog = newCatalog(temporaryDirectory);
        addUnresolvableDtd(catalog, MATCHING_PUBLIC_ID, "inline-missing.dtd");
        addUnresolvableEntity(catalog, MATCHING_URI, "inline-missing.xml");
        addExternalCatalogPath(catalog, temporaryDirectory);

        InputSource matchingEntity = catalog.resolveEntity(MATCHING_PUBLIC_ID, "urn:system:inline-missing");
        InputSource unmatchedEntity = catalog.resolveEntity(UNMATCHED_PUBLIC_ID, "urn:system:unmatched-missing");

        assertThat(matchingEntity).isNull();
        assertThat(unmatchedEntity).isNull();
        assertExternalResolverAttempt(() -> catalog.resolve(UNMATCHED_URI, null));
        assertExternalResolverAttempt(() -> catalog.resolve(MATCHING_URI, temporaryDirectory.toUri().toString()));
    }

    private static void assertExternalResolverAttempt(SourceLookup lookup) throws Exception {
        try {
            assertThat(lookup.resolve()).isNotNull();
        } catch (RuntimeException ex) {
            assertThat(ex).hasRootCauseInstanceOf(MalformedURLException.class);
        }
    }

    private static XMLCatalog newCatalog(Path baseDirectory) {
        Project project = new Project();
        project.setBaseDir(baseDirectory.toFile());

        XMLCatalog catalog = new XMLCatalog();
        catalog.setProject(project);
        return catalog;
    }

    private static void addUnresolvableDtd(XMLCatalog catalog, String publicId, String location) {
        ResourceLocation dtd = new ResourceLocation();
        dtd.setPublicId(publicId);
        dtd.setLocation(location);
        catalog.addDTD(dtd);
    }

    private static void addUnresolvableEntity(XMLCatalog catalog, String publicId, String location) {
        ResourceLocation entity = new ResourceLocation();
        entity.setPublicId(publicId);
        entity.setLocation(location);
        catalog.addEntity(entity);
    }

    private static void addExternalCatalogPath(XMLCatalog catalog, Path temporaryDirectory) throws Exception {
        Path catalogFile = temporaryDirectory.resolve("external-catalog.cat");
        Files.write(catalogFile, externalCatalog().getBytes(StandardCharsets.UTF_8));
        catalog.createCatalogPath().setLocation(catalogFile.toFile());
    }

    private static String externalCatalog() {
        return "PUBLIC \"-//Example//DTD External Missing//EN\" \"external-missing.dtd\"\n"
                + "URI \"urn:example:external-missing\" \"external-missing.xml\"\n";
    }

    private interface SourceLookup {
        Source resolve() throws Exception;
    }
}
