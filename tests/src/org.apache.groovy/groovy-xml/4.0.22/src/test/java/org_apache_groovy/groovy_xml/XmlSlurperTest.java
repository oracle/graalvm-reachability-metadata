/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_xml;

import groovy.lang.Closure;
import groovy.xml.XmlSlurper;
import groovy.xml.slurpersupport.GPathResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlSlurperTest {
    private static final String CATALOG = """
            <catalog region="EU">
              <book id="b1" category="programming">
                <title>Groovy &amp; XML</title>
                <pages>320</pages>
              </book>
              <book id="b2" category="reference">
                <title>Native Image</title>
                <pages>180</pages>
              </book>
            </catalog>
            """;

    @Test
    void navigatesElementsAttributesAndText() throws Exception {
        GPathResult catalog = new XmlSlurper().parseText(CATALOG);
        GPathResult books = (GPathResult) catalog.getProperty("book");
        GPathResult second = (GPathResult) books.getAt(1);

        assertThat(catalog.name()).isEqualTo("catalog");
        assertThat(((GPathResult) catalog.getProperty("@region")).text()).isEqualTo("EU");
        assertThat(books.size()).isEqualTo(2);
        assertThat(((GPathResult) second.getProperty("title")).text()).isEqualTo("Native Image");
        assertThat(((GPathResult) second.getProperty("@category")).text()).isEqualTo("reference");
        assertThat(((GPathResult) second.getProperty("pages")).toInteger()).isEqualTo(180);
        assertThat(((GPathResult) books.getProperty("title")).text()).isEqualTo("Groovy & XMLNative Image");
        assertThat(((GPathResult) second.getProperty("..")).name()).isEqualTo("catalog");
        assertThat(((GPathResult) catalog.getProperty("missing")).isEmpty()).isTrue();
    }

    @Test
    void iteratesChildrenAndDescendants() throws Exception {
        GPathResult catalog = new XmlSlurper().parseText(CATALOG);

        List<String> ids = new ArrayList<>();
        for (Object book : ((GPathResult) catalog.getProperty("book")).list()) {
            ids.add(((GPathResult) ((GPathResult) book).getProperty("@id")).text());
        }
        List<String> descendants = new ArrayList<>();
        Iterator<?> depthFirst = catalog.depthFirst();
        while (depthFirst.hasNext()) {
            descendants.add(((GPathResult) depthFirst.next()).name());
        }

        assertThat(ids).containsExactly("b1", "b2");
        assertThat(catalog.children().size()).isEqualTo(2);
        assertThat(descendants).containsExactly("catalog", "book", "title", "pages", "book", "title", "pages");
    }

    @Test
    void filtersResultsWithClosures() throws Exception {
        GPathResult books = (GPathResult) new XmlSlurper().parseText(CATALOG).getProperty("book");

        GPathResult reference = books.find(new Closure<Boolean>(this) {
            public Boolean doCall(Object book) {
                return "reference".equals(((GPathResult) ((GPathResult) book).getProperty("@category")).text());
            }
        });
        GPathResult longBooks = books.findAll(new Closure<Boolean>(this) {
            public Boolean doCall(Object book) {
                return ((GPathResult) ((GPathResult) book).getProperty("pages")).toInteger() > 200;
            }
        });

        assertThat(((GPathResult) reference.getProperty("@id")).text()).isEqualTo("b2");
        assertThat(longBooks.size()).isEqualTo(1);
        assertThat(((GPathResult) longBooks.getProperty("title")).text()).isEqualTo("Groovy & XML");
    }

    @Test
    void resolvesDeclaredNamespacePrefixes() throws Exception {
        String xml = """
                <bk:catalog xmlns:bk="urn:books" xmlns:meta="urn:meta">
                  <bk:book meta:id="b1"><bk:title>Groovy</bk:title></bk:book>
                  <other:book xmlns:other="urn:other"><other:title>Other</other:title></other:book>
                </bk:catalog>
                """;

        GPathResult catalog = new XmlSlurper(false, true).parseText(xml)
                .declareNamespace(Map.of("b", "urn:books", "m", "urn:meta"));
        GPathResult books = (GPathResult) catalog.getProperty("b:book");

        assertThat(books.size()).isEqualTo(1);
        assertThat(((GPathResult) books.getProperty("b:title")).text()).isEqualTo("Groovy");
        assertThat(((GPathResult) books.getProperty("@m:id")).text()).isEqualTo("b1");
        assertThat(((GPathResult) catalog.getProperty("book")).size()).isEqualTo(2);
        assertThat(catalog.lookupNamespace("b")).isEqualTo("urn:books");
    }

    @Test
    void keepsWhitespaceWhenRequested() throws Exception {
        XmlSlurper slurper = new XmlSlurper();
        slurper.setKeepWhitespace(true);

        GPathResult note = slurper.parseText("<note><body>  padded  </body></note>");

        assertThat(((GPathResult) note.getProperty("body")).text()).isEqualTo("  padded  ");
    }

    @Test
    void parsesFilesAndReaders(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("catalog.xml");
        Files.writeString(file, CATALOG, StandardCharsets.UTF_8);
        XmlSlurper slurper = new XmlSlurper();

        GPathResult fromFile = slurper.parse(file.toFile());
        GPathResult fromPath = slurper.parse(file);
        GPathResult fromReader = slurper.parse(new StringReader(CATALOG));

        for (GPathResult catalog : List.of(fromFile, fromPath, fromReader)) {
            assertThat(((GPathResult) catalog.getProperty("book")).size()).isEqualTo(2);
            assertThat(((GPathResult) catalog.getProperty("@region")).text()).isEqualTo("EU");
        }
    }
}
