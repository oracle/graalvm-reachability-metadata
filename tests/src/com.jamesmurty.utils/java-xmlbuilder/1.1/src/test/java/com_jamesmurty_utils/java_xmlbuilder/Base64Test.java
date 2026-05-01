/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jamesmurty_utils.java_xmlbuilder;

import com.jamesmurty.utils.XMLBuilder;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.transform.OutputKeys;

import static org.assertj.core.api.Assertions.assertThat;

public class Base64Test {
    @Test
    void xmlDocumentCanBeBuiltSerializedParsedAndQueried() throws Exception {
        XMLBuilder builder = XMLBuilder.create("catalog")
                .namespace("bk", "https://example.com/book")
                .e("bk:book")
                .a("id", "java-xmlbuilder")
                .e("title")
                .t("Reachability Metadata")
                .up()
                .e("description")
                .data("<native-image ready>")
                .up()
                .up()
                .e("count")
                .t("1");
        Properties outputProperties = new Properties();
        outputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        String xml = builder.asString(outputProperties);
        XMLBuilder parsed = XMLBuilder.parse(new InputSource(new StringReader(xml)));
        NamespaceContext namespaceContext = namespaceContext("bk", "https://example.com/book");
        XMLBuilder book = parsed.xpathFind("/catalog/bk:book", namespaceContext);
        XMLBuilder title = parsed.xpathFind("/catalog/bk:book/title", namespaceContext);
        XMLBuilder description = parsed.xpathFind("/catalog/bk:book/description", namespaceContext);
        XMLBuilder count = parsed.xpathFind("/catalog/count");

        assertThat(xml).contains("xmlns:bk=\"https://example.com/book\"");
        assertThat(book.getElement().getAttribute("id")).isEqualTo("java-xmlbuilder");
        assertThat(title.getElement().getTextContent()).isEqualTo("Reachability Metadata");
        assertThat(description.getElement().getTextContent()).isEqualTo("<native-image ready>");
        assertThat(count.getElement().getTextContent()).isEqualTo("1");
    }

    private static NamespaceContext namespaceContext(String prefix, String namespaceUri) {
        return new NamespaceContext() {
            @Override
            public String getNamespaceURI(String requestedPrefix) {
                if (prefix.equals(requestedPrefix)) {
                    return namespaceUri;
                }
                return XMLConstants.NULL_NS_URI;
            }

            @Override
            public String getPrefix(String requestedNamespaceUri) {
                if (namespaceUri.equals(requestedNamespaceUri)) {
                    return prefix;
                }
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String requestedNamespaceUri) {
                if (namespaceUri.equals(requestedNamespaceUri)) {
                    return Collections.singleton(prefix).iterator();
                }
                return Collections.emptyIterator();
            }
        };
    }
}
