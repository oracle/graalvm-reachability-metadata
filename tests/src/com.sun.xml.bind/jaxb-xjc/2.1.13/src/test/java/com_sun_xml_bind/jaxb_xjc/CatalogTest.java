/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_xjc;

import com.sun.org.apache.xml.internal.resolver.Catalog;
import com.sun.org.apache.xml.internal.resolver.CatalogManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class CatalogTest {
    @TempDir
    Path directory;

    @Test
    void resolvesIdentifiersThroughSubordinateCatalogs() throws Exception {
        Path child = directory.resolve("child.xml");
        Files.writeString(child, """
                <catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">
                  <system systemId="urn:inventory:schema" uri="inventory.xsd"/>
                </catalog>
                """);
        Path parent = directory.resolve("parent.xml");
        Files.writeString(parent, """
                <catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">
                  <nextCatalog catalog="child.xml"/>
                </catalog>
                """);
        CatalogManager manager = new CatalogManager("catalog-manager.properties");
        Catalog catalog = new Catalog(manager);
        catalog.setupReaders();
        catalog.parseCatalog(parent.toUri().toURL());

        String resolved = catalog.resolveSystem("urn:inventory:schema");
        assertThat(Path.of(new URI(resolved))).isEqualTo(directory.resolve("inventory.xsd"));
        assertThat(catalog.resolveSystem("urn:inventory:unknown")).isNull();
        assertThat(catalog.resolveSystem("urn:inventory:schema")).isEqualTo(resolved);
    }
}
