/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_dtd_parser.dtd_parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.xml.dtdparser.Resolver;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public class ResolverTest {
    private static final String PUBLIC_ID = "-//GraalVM Reachability Metadata//DTD Resolver Test//EN";
    private static final String RESOURCE_NAME = "com_sun_xml_dtd_parser/dtd_parser/resources/resolver-catalog.dtd";
    private static final String RESOURCE_TEXT = "<!ELEMENT greeting (#PCDATA)>\n";

    @Test
    void returnsNullWhenRegisteredSystemResourceAndFallbackUriAreMissing() throws Exception {
        Resolver resolver = new Resolver();
        resolver.registerCatalogEntry(PUBLIC_ID, "missing/resource.dtd", null);

        InputSource source = resolver.resolveEntity(PUBLIC_ID, null);

        assertThat(source).isNull();
    }

    @Test
    void resolvesCatalogEntryFromProvidedClassLoaderResource() throws Exception {
        Resolver resolver = new Resolver();
        resolver.registerCatalogEntry(PUBLIC_ID, RESOURCE_NAME, new TestResourceClassLoader());

        InputSource source = resolver.resolveEntity(PUBLIC_ID, "file:/not-used-class-loader-resource.dtd");

        assertThat(source.getPublicId()).isEqualTo(PUBLIC_ID);
        assertThat(source.getSystemId()).isEqualTo("java:resource:" + RESOURCE_NAME);
        assertThat(readText(source)).contains(RESOURCE_TEXT.trim());
    }

    private static String readText(InputSource source) throws IOException {
        try (Reader reader = source.getCharacterStream()) {
            StringWriter writer = new StringWriter();
            reader.transferTo(writer);
            return writer.toString();
        }
    }

    private static final class TestResourceClassLoader extends ClassLoader {
        @Override
        public InputStream getResourceAsStream(String name) {
            if (!RESOURCE_NAME.equals(name)) {
                return null;
            }
            return new ByteArrayInputStream(RESOURCE_TEXT.getBytes(StandardCharsets.UTF_8));
        }
    }
}
