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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class CatalogManagerTest {
    @TempDir
    Path directory;

    @Test
    void configuresPrivateCatalogsFromProperties() throws Exception {
        CatalogManager manager = new CatalogManager("catalog-manager.properties");
        Catalog catalog = manager.getPrivateCatalog();
        String entries = """
                <catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">
                  <system systemId="urn:orders:schema" uri="file:/schemas/orders.xsd"/>
                </catalog>
                """;
        Path source = directory.resolve("catalog.xml");
        Files.writeString(source, entries);
        catalog.parseCatalog(source.toUri().toURL());

        assertThat(catalog.resolveSystem("urn:orders:schema")).isEqualTo("file:/schemas/orders.xsd");
        assertThat(manager.getPreferPublic()).isTrue();
        assertThat(manager.getAllowOasisXMLCatalogPI()).isFalse();
        Catalog other = manager.getPrivateCatalog();
        assertThat(other).isNotSameAs(catalog);
        assertThat(other.resolveSystem("urn:orders:schema")).isNull();
    }
}
