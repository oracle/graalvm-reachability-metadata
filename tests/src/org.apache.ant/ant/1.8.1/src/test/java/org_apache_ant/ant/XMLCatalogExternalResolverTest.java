/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.ResourceLocation;
import org.apache.tools.ant.types.XMLCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

public class XMLCatalogExternalResolverTest {
    private static final String EXTERNAL_SYSTEM_ID = "https://example.invalid/external.dtd";
    private static final String EXTERNAL_URI_PREFIX = "https://example.invalid/schema/";
    private static final String EXTERNAL_URI = EXTERNAL_URI_PREFIX + "schema.xsd";

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesEntitiesAndUrisThroughExternalCatalogs() throws Exception {
        Path entity = write("entity.dtd", "<!ELEMENT sample EMPTY>");
        Path schema = write("schema.xsd", "<schema xmlns=\"http://www.w3.org/2001/XMLSchema\"/>");
        Path catalogFile = write("catalog", """
                SYSTEM \"%s\" \"%s\"
                REWRITE_URI \"%s\" \"%s\"
                """.formatted(EXTERNAL_SYSTEM_ID, entity.toUri(), EXTERNAL_URI_PREFIX,
                temporaryDirectory.toUri()));

        InputSource externalEntity = catalog(catalogFile).resolveEntity(null, EXTERNAL_SYSTEM_ID);
        assertThat(path(externalEntity.getSystemId())).isEqualTo(entity);

        XMLCatalog entityCatalog = catalog(catalogFile);
        entityCatalog.addDTD(location("public-id", temporaryDirectory.resolve("missing.dtd").toString()));
        InputSource localEntity = entityCatalog.resolveEntity("public-id", EXTERNAL_SYSTEM_ID);
        assertThat(path(localEntity.getSystemId())).isEqualTo(entity);

        assertThat(path(systemId(catalog(catalogFile).resolve(EXTERNAL_URI, null))))
                .isEqualTo(schema);

        XMLCatalog uriCatalog = catalog(catalogFile);
        uriCatalog.addEntity(location(EXTERNAL_URI, temporaryDirectory.resolve("missing.xsd").toString()));
        assertThat(path(systemId(uriCatalog.resolve(EXTERNAL_URI, null))))
                .isEqualTo(schema);
    }

    private XMLCatalog catalog(Path catalogFile) {
        Project project = new Project();
        project.init();
        XMLCatalog catalog = new XMLCatalog();
        catalog.setProject(project);
        catalog.createCatalogPath().setLocation(catalogFile.toFile());
        return catalog;
    }

    private ResourceLocation location(String publicId, String resource) {
        ResourceLocation location = new ResourceLocation();
        location.setPublicId(publicId);
        location.setLocation(resource);
        return location;
    }

    private String systemId(Source source) {
        assertThat(source).isInstanceOf(SAXSource.class);
        return ((SAXSource) source).getInputSource().getSystemId();
    }

    private Path path(String systemId) {
        return Path.of(URI.create(systemId));
    }

    private Path write(String name, String content) throws IOException {
        Path file = temporaryDirectory.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
