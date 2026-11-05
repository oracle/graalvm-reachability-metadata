/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_xml;

import groovy.namespace.QName;
import groovy.util.Node;
import groovy.util.NodeList;
import groovy.xml.Namespace;
import groovy.xml.XmlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlParserTest {
    private static final String CATALOG = """
            <catalog region="EU">
              <book id="b1" category="programming">
                <title>Groovy &amp; XML</title>
                <price>39.5</price>
              </book>
              <book id="b2" category="reference">
                <title>Native Image</title>
                <price>12</price>
              </book>
            </catalog>
            """;

    @Test
    void parsesTextIntoNavigableNodeTree() throws Exception {
        Node catalog = new XmlParser().parseText(CATALOG);

        assertThat(catalog.name()).isEqualTo("catalog");
        assertThat(catalog.attribute("region")).isEqualTo("EU");
        assertThat(catalog.get("@region")).isEqualTo("EU");

        NodeList books = (NodeList) catalog.get("book");
        assertThat(books).hasSize(2);
        Node first = (Node) books.get(0);
        assertThat(first.attributes()).containsEntry("id", "b1").containsEntry("category", "programming");
        assertThat(first.get("title")).isInstanceOf(NodeList.class);
        assertThat(((NodeList) first.get("title")).text()).isEqualTo("Groovy & XML");
        assertThat(books.getAt("price").text()).isEqualTo("39.512");
        assertThat(((Node) ((NodeList) first.get("price")).get(0)).toDouble()).isEqualTo(39.5);
        assertThat(first.parent()).isSameAs(catalog);
    }

    @Test
    void walksTreeDepthFirstAndBreadthFirst() throws Exception {
        Node catalog = new XmlParser().parseText(CATALOG);

        List<Object> depthFirst = new ArrayList<>();
        for (Object node : catalog.depthFirst()) {
            depthFirst.add(((Node) node).name());
        }
        List<Object> breadthFirst = new ArrayList<>();
        for (Object node : catalog.breadthFirst()) {
            breadthFirst.add(((Node) node).name());
        }

        assertThat(depthFirst).containsExactly("catalog", "book", "title", "price", "book", "title", "price");
        assertThat(breadthFirst).containsExactly("catalog", "book", "book", "title", "price", "title", "price");
    }

    @Test
    void mutatesParsedTreeThroughNodeApi() throws Exception {
        Node catalog = new XmlParser().parseText(CATALOG);

        Node added = catalog.appendNode("book", new LinkedHashMap<>(Map.of("id", "b3")));
        added.appendNode("title", "Reachability Metadata");
        Node removed = (Node) ((NodeList) catalog.get("book")).get(1);
        assertThat(catalog.remove(removed)).isTrue();
        added.attributes().put("category", "testing");

        NodeList books = (NodeList) catalog.get("book");
        List<Object> ids = new ArrayList<>();
        for (Object book : books) {
            ids.add(((Node) book).attribute("id"));
        }
        assertThat(ids).containsExactly("b1", "b3");
        assertThat(added.attribute("category")).isEqualTo("testing");
        assertThat(books.getAt("title").text()).isEqualTo("Groovy & XMLReachability Metadata");
    }

    @Test
    void controlsWhitespaceHandling() throws Exception {
        String xml = "<note><body>  padded text  </body><empty>   </empty></note>";
        XmlParser defaults = new XmlParser();
        XmlParser trimming = new XmlParser();
        trimming.setTrimWhitespace(true);
        XmlParser keeping = new XmlParser();
        keeping.setKeepIgnorableWhitespace(true);

        Node byDefault = defaults.parseText(xml);
        Node trimmed = trimming.parseText(xml);
        Node kept = keeping.parseText(xml);

        assertThat(defaults.isTrimWhitespace()).isFalse();
        assertThat(((NodeList) byDefault.get("body")).text()).isEqualTo("  padded text  ");
        assertThat(firstChild(byDefault, "empty").children()).isEmpty();
        assertThat(trimming.isTrimWhitespace()).isTrue();
        assertThat(((NodeList) trimmed.get("body")).text()).isEqualTo("padded text");
        assertThat(keeping.isKeepIgnorableWhitespace()).isTrue();
        assertThat(firstChild(kept, "empty").children()).containsExactly("   ");
    }

    @Test
    void resolvesNamespacedElementsAsQualifiedNames() throws Exception {
        String xml = """
                <bk:catalog xmlns:bk="urn:books" xmlns:meta="urn:meta">
                  <bk:book meta:id="b1"><bk:title>Groovy</bk:title></bk:book>
                </bk:catalog>
                """;
        Namespace books = new Namespace("urn:books", "bk");
        Namespace meta = new Namespace("urn:meta", "meta");

        Node catalog = new XmlParser(false, true).parseText(xml);
        QName catalogName = (QName) catalog.name();
        NodeList titles = catalog.getAt(books.get("book")).getAt(books.get("title"));
        Node book = (Node) catalog.getAt(books.get("book")).get(0);

        assertThat(catalogName.getNamespaceURI()).isEqualTo("urn:books");
        assertThat(catalogName.getLocalPart()).isEqualTo("catalog");
        assertThat(catalogName.matches("bk:catalog")).isTrue();
        assertThat(titles.text()).isEqualTo("Groovy");
        assertThat(book.attribute(meta.get("id"))).isEqualTo("b1");
        assertThat(books.getPrefix()).isEqualTo("bk");
        assertThat(books.getUri()).isEqualTo("urn:books");
    }

    @Test
    void parsesFilesStreamsAndReaders(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("catalog.xml");
        Files.writeString(file, CATALOG, StandardCharsets.UTF_8);
        XmlParser parser = new XmlParser();

        Node fromFile = parser.parse(file.toFile());
        Node fromStream = parser.parse(new ByteArrayInputStream(CATALOG.getBytes(StandardCharsets.UTF_8)));
        Node fromReader = parser.parse(new StringReader(CATALOG));

        for (Node catalog : List.of(fromFile, fromStream, fromReader)) {
            assertThat(catalog.name()).isEqualTo("catalog");
            assertThat(((NodeList) catalog.get("book")).getAt("title").text()).isEqualTo("Groovy & XMLNative Image");
        }
    }

    private static Node firstChild(Node parent, String name) {
        return (Node) ((NodeList) parent.get(name)).get(0);
    }
}
