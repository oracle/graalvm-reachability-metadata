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
import java.util.Properties;

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
        XMLBuilder book = parsed.xpathFind("/catalog/bk:book", parsed.buildDocumentNamespaceContext());
        XMLBuilder title = parsed.xpathFind("/catalog/bk:book/title", parsed.buildDocumentNamespaceContext());
        XMLBuilder description = parsed.xpathFind("/catalog/bk:book/description", parsed.buildDocumentNamespaceContext());
        XMLBuilder count = parsed.xpathFind("/catalog/count");

        assertThat(xml).contains("xmlns:bk=\"https://example.com/book\"");
        assertThat(book.getElement().getAttribute("id")).isEqualTo("java-xmlbuilder");
        assertThat(title.getElement().getTextContent()).isEqualTo("Reachability Metadata");
        assertThat(description.getElement().getTextContent()).isEqualTo("<native-image ready>");
        assertThat(count.getElement().getTextContent()).isEqualTo("1");
    }
}
