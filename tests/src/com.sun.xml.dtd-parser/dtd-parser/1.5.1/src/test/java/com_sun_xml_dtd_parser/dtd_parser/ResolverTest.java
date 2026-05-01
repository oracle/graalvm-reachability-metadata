/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_dtd_parser.dtd_parser;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;

import com.sun.xml.dtdparser.Resolver;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

public class ResolverTest {
    private static final String PUBLIC_ID = "-//Example//DTD Widget//EN";
    private static final String RESOURCE_NAME = "com_sun_xml_dtd_parser/dtd_parser/resources/widget.dtd";

    @Test
    void resolveEntityLoadsRegisteredResourceWithSystemClassLoader() throws Exception {
        Resolver resolver = new Resolver();
        resolver.registerCatalogEntry(PUBLIC_ID, RESOURCE_NAME, null);

        InputSource source = resolver.resolveEntity(PUBLIC_ID, "file:/unreachable-widget.dtd");

        assertResolvedCatalogResource(source);
    }

    @Test
    void resolveEntityLoadsRegisteredResourceWithExplicitClassLoader() throws Exception {
        Resolver resolver = new Resolver();
        resolver.registerCatalogEntry(PUBLIC_ID, RESOURCE_NAME, ResolverTest.class.getClassLoader());

        InputSource source = resolver.resolveEntity(PUBLIC_ID, "file:/unreachable-widget.dtd");

        assertResolvedCatalogResource(source);
    }

    private static void assertResolvedCatalogResource(InputSource source) throws IOException {
        assertThat(source.getPublicId()).isEqualTo(PUBLIC_ID);
        assertThat(source.getSystemId()).isEqualTo("java:resource:" + RESOURCE_NAME);
        assertThat(read(source.getCharacterStream()))
                .contains("<!ELEMENT widget (#PCDATA)>")
                .contains("<!ATTLIST widget id ID #IMPLIED>");
    }

    private static String read(Reader reader) throws IOException {
        StringWriter writer = new StringWriter();
        char[] buffer = new char[256];
        int count;
        try (reader) {
            while ((count = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, count);
            }
        }
        return writer.toString();
    }
}
