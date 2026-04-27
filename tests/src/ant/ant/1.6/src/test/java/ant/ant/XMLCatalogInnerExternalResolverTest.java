/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void delegatesUnresolvedCatalogLookupsToTheExternalResolver(@TempDir Path temporaryDirectory) throws Exception {
        String matchingUri = temporaryDirectory.resolve("inline-missing.xml").toUri().toString();
        String unmatchedUri = temporaryDirectory.resolve("unmatched-missing.xml").toUri().toString();
        XMLCatalog catalog = newCatalog(temporaryDirectory);
        addUnresolvableDtd(catalog, MATCHING_PUBLIC_ID, "inline-missing.dtd");
        addUnresolvableEntity(catalog, matchingUri, "inline-missing.xml");
        addExternalCatalogPath(catalog, temporaryDirectory);

        InputSource matchingEntity = catalog.resolveEntity(MATCHING_PUBLIC_ID, "urn:system:inline-missing");
        InputSource unmatchedEntity = catalog.resolveEntity(UNMATCHED_PUBLIC_ID, "urn:system:unmatched-missing");
        Source unmatchedSource = catalog.resolve(unmatchedUri, null);
        Source matchingSource = catalog.resolve(matchingUri, null);

        assertThat(matchingEntity).isNull();
        assertThat(unmatchedEntity).isNull();
        assertThat(unmatchedSource).isNotNull();
        assertThat(matchingSource).isNotNull();
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
}
