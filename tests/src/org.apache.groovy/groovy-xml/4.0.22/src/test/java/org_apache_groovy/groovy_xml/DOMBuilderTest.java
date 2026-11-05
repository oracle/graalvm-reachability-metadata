/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_xml;

import groovy.xml.DOMBuilder;
import groovy.xml.NamespaceBuilder;
import groovy.xml.NamespaceBuilderSupport;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DOMBuilderTest {
    @Test
    void parsesReaderAndTextIntoDomDocuments() throws Exception {
        String xml = """
                <catalog region="EU">
                  <book id="b1"><title>Groovy</title></book>
                  <book id="b2"><title>Native Image</title></book>
                </catalog>
                """;

        Document fromReader = DOMBuilder.parse(new StringReader(xml));
        Document fromText = DOMBuilder.newInstance().parseText(xml);

        for (Document document : new Document[] {fromReader, fromText}) {
            Element catalog = document.getDocumentElement();
            assertThat(catalog.getTagName()).isEqualTo("catalog");
            assertThat(catalog.getAttribute("region")).isEqualTo("EU");
            assertThat(catalog.getElementsByTagName("book").getLength()).isEqualTo(2);
            assertThat(catalog.getElementsByTagName("title").item(1).getTextContent()).isEqualTo("Native Image");
        }
    }

    @Test
    void parsesNamespaceAwareDocuments() throws Exception {
        String xml = "<bk:catalog xmlns:bk=\"urn:books\"><bk:book>Groovy</bk:book></bk:catalog>";

        Document document = DOMBuilder.parse(new StringReader(xml), false, true);
        Element catalog = document.getDocumentElement();

        assertThat(catalog.getNamespaceURI()).isEqualTo("urn:books");
        assertThat(catalog.getLocalName()).isEqualTo("catalog");
        assertThat(catalog.getElementsByTagNameNS("urn:books", "book").item(0).getTextContent()).isEqualTo("Groovy");
    }

    @Test
    void buildsDomDocumentFromNestedBuilderCalls() throws Exception {
        DOMBuilder builder = DOMBuilder.newInstance();

        Object root = builder.invokeMethod("catalog", new Object[] {Map.of("region", "EU"), new BuilderBody(this, () -> {
            builder.invokeMethod("book", new Object[] {Map.of("id", "b1"), new BuilderBody(this, () -> {
                builder.invokeMethod("title", new Object[] {"Groovy & XML"});
                builder.invokeMethod("pages", new Object[] {Map.of("unit", "page"), 320});
            })});
            builder.invokeMethod("book", new Object[] {Map.of("id", "b2")});
        })});

        Element catalog = (Element) root;
        Element firstBook = (Element) catalog.getElementsByTagName("book").item(0);
        Element pages = (Element) firstBook.getElementsByTagName("pages").item(0);
        assertThat(catalog.getAttribute("region")).isEqualTo("EU");
        assertThat(catalog.getElementsByTagName("book").getLength()).isEqualTo(2);
        assertThat(firstBook.getAttribute("id")).isEqualTo("b1");
        assertThat(firstBook.getElementsByTagName("title").item(0).getTextContent()).isEqualTo("Groovy & XML");
        assertThat(pages.getAttribute("unit")).isEqualTo("page");
        assertThat(pages.getTextContent()).isEqualTo("320");
    }

    @Test
    void buildsNamespacedElementsThroughNamespaceBuilder() throws Exception {
        DOMBuilder dom = DOMBuilder.newInstance();
        NamespaceBuilderSupport books = NamespaceBuilder.newInstance(dom, "urn:books", "bk");

        Object root = books.invokeMethod("catalog", new Object[] {new BuilderBody(this, () -> {
            books.invokeMethod("book", new Object[] {new LinkedHashMap<>(Map.of("id", "b1")), "Groovy"});
        })});

        Element catalog = (Element) root;
        Element book = (Element) catalog.getElementsByTagNameNS("urn:books", "book").item(0);
        assertThat(catalog.getNamespaceURI()).isEqualTo("urn:books");
        assertThat(catalog.getTagName()).isEqualTo("bk:catalog");
        assertThat(book.getAttribute("id")).isEqualTo("b1");
        assertThat(book.getTextContent()).isEqualTo("Groovy");
    }
}
