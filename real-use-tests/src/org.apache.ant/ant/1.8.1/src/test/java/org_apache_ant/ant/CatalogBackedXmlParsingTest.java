/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.XMLCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Ant's XML catalog resolution subsystem while an application parses an offline document.
public class CatalogBackedXmlParsingTest {
    private static final String DTD_SYSTEM_ID = "https://example.invalid/inventory.dtd";

    @Test
    @Timeout(60)
    public void parsesDocumentWithCatalogMappedExternalDtd() throws Exception {
        Path workDirectory = Files.createTempDirectory("ant-catalog-xml-");
        try {
            Path dtd = write(workDirectory, "inventory.dtd", "<!ELEMENT inventory (item)>\n"
                    + "<!ELEMENT item EMPTY>\n"
                    + "<!ATTLIST item sku CDATA #REQUIRED>\n");
            Path catalogFile = write(workDirectory, "catalog", "SYSTEM \"" + DTD_SYSTEM_ID
                    + "\" \"" + dtd.toUri() + "\"\n");
            Path document = write(workDirectory, "inventory.xml", "<?xml version=\"1.0\"?>\n"
                    + "<!DOCTYPE inventory SYSTEM \"" + DTD_SYSTEM_ID + "\">\n"
                    + "<inventory><item sku=\"A-17\"/></inventory>\n");

            Project project = new Project();
            project.init();
            XMLCatalog catalog = new XMLCatalog();
            catalog.setProject(project);
            catalog.createCatalogPath().setLocation(catalogFile.toFile());

            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            parserFactory.setNamespaceAware(true);
            parserFactory.setValidating(true);
            XMLReader reader = parserFactory.newSAXParser().getXMLReader();
            InventoryHandler handler = new InventoryHandler();
            reader.setEntityResolver(catalog);
            reader.setContentHandler(handler);
            reader.parse(document.toUri().toString());

            assertEquals("inventory", handler.rootElement);
            assertEquals("A-17", handler.itemSku);
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private static Path write(Path directory, String name, String content) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(CatalogBackedXmlParsingTest::delete);
            }
        }
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not clean up " + path, exception);
        }
    }

    private static final class InventoryHandler extends DefaultHandler {
        private String rootElement;
        private String itemSku;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            if (rootElement == null) {
                rootElement = qName;
            } else if ("item".equals(qName)) {
                itemSku = attributes.getValue("sku");
            }
        }
    }
}
