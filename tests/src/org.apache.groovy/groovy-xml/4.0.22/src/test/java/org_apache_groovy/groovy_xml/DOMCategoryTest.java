/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_xml;

import groovy.xml.DOMBuilder;
import groovy.xml.dom.DOMCategory;
import org.apache.groovy.xml.extensions.XmlExtensions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DOMCategoryTest {
    private static final String CATALOG = """
            <catalog region="EU">
              <book id="b1"><title>Groovy</title><pages>320</pages></book>
              <book id="b2"><title>Native Image</title><pages>180</pages></book>
            </catalog>
            """;

    @Test
    void navigatesElementsWithGPathStyleLookups() throws Exception {
        Element catalog = DOMBuilder.parse(new StringReader(CATALOG)).getDocumentElement();

        NodeList books = (NodeList) DOMCategory.get(catalog, "book");
        Element second = (Element) books.item(1);
        NodeList titles = (NodeList) DOMCategory.get(books, "title");

        assertThat(DOMCategory.size(books)).isEqualTo(2);
        assertThat(DOMCategory.get(catalog, "@region")).isEqualTo("EU");
        assertThat(DOMCategory.name(second)).isEqualTo("book");
        assertThat(DOMCategory.get(second, "@id")).isEqualTo("b2");
        assertThat(DOMCategory.text(titles)).isEqualTo("GroovyNative Image");
        assertThat(DOMCategory.parent(second)).isSameAs(catalog);
        assertThat(DOMCategory.size(DOMCategory.children(catalog))).isEqualTo(2);
        assertThat(DOMCategory.xpath(catalog, "sum(book/pages)")).isEqualTo("500");
    }

    @Test
    void walksDescendantsDepthFirstAndBreadthFirst() throws Exception {
        Element catalog = DOMBuilder.parse(new StringReader(CATALOG)).getDocumentElement();

        List<String> depthFirst = new ArrayList<>();
        for (Node node : DOMCategory.list(DOMCategory.depthFirst(catalog))) {
            depthFirst.add(DOMCategory.name(node));
        }
        List<String> breadthFirst = new ArrayList<>();
        for (Node node : DOMCategory.list(DOMCategory.breadthFirst(catalog))) {
            breadthFirst.add(DOMCategory.name(node));
        }

        assertThat(depthFirst).containsExactly("catalog", "book", "title", "pages", "book", "title", "pages");
        assertThat(breadthFirst).containsExactly(
                "catalog", "book", "book", "title", "pages", "title", "pages", "#text", "#text", "#text", "#text");
    }

    @Test
    void appendsAndUpdatesElements() throws Exception {
        Element catalog = DOMBuilder.parse(new StringReader(CATALOG)).getDocumentElement();

        Element added = DOMCategory.appendNode(catalog, "book", Map.of("id", "b3"));
        Element title = DOMCategory.appendNode(added, "title", "Reachability Metadata");
        DOMCategory.putAt(added, "@category", "testing");
        DOMCategory.setValue(title, "Reachability Metadata Guide");

        NodeList books = (NodeList) DOMCategory.get(catalog, "book");
        assertThat(DOMCategory.size(books)).isEqualTo(3);
        assertThat(added.getAttribute("category")).isEqualTo("testing");
        assertThat(DOMCategory.text(title)).isEqualTo("Reachability Metadata Guide");
        assertThat(DOMCategory.localText(title)).containsExactly("Reachability Metadata Guide");
    }

    @Test
    void iteratesAndSerializesThroughXmlExtensions() throws Exception {
        Element catalog = DOMBuilder.parse(new StringReader(CATALOG)).getDocumentElement();

        List<String> ids = new ArrayList<>();
        Iterator<Node> books = XmlExtensions.iterator(catalog.getElementsByTagName("book"));
        while (books.hasNext()) {
            ids.add(((Element) books.next()).getAttribute("id"));
        }
        String serialized = XmlExtensions.serialize((Element) catalog.getElementsByTagName("book").item(0));

        assertThat(ids).containsExactly("b1", "b2");
        assertThat(serialized).doesNotContain("<?xml");
        assertThat(serialized).contains("<book id=\"b1\">").contains("<title>Groovy</title>").contains("<pages>320</pages>");
    }
}
