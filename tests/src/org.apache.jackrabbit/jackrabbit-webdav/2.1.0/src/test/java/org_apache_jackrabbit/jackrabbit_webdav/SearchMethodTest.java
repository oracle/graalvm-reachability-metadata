/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.SearchMethod;
import org.apache.jackrabbit.webdav.search.SearchConstants;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class SearchMethodTest {
    @Test
    void createsSearchRequestWithLanguageNamespaceAndStatement() throws Exception {
        Namespace languageNamespace = Namespace.getNamespace("jcr", "http://www.day.com/jcr/webdav/1.0");

        SearchMethod method = new SearchMethod(
                "/repository/default/",
                "//element(*, nt:file)",
                "xpath",
                languageNamespace);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_SEARCH);
        assertThat(method.getPath()).isEqualTo("/repository/default/");

        Document requestDocument = parseRequestEntity(method.getRequestEntity());
        Element searchRequestElement = requestDocument.getDocumentElement();
        assertThat(DomUtil.matches(
                searchRequestElement,
                SearchConstants.XML_SEARCHREQUEST,
                SearchConstants.NAMESPACE)).isTrue();

        Element languageElement = DomUtil.getChildElement(searchRequestElement, "xpath", languageNamespace);
        assertThat(languageElement).isNotNull();
        assertThat(DomUtil.getText(languageElement)).isEqualTo("//element(*, nt:file)");
    }

    private static Document parseRequestEntity(RequestEntity requestEntity) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        requestEntity.writeRequest(output);
        DocumentBuilder builder = newDocumentBuilder();
        return builder.parse(new InputSource(new ByteArrayInputStream(output.toByteArray())));
    }

    private static DocumentBuilder newDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder();
    }
}
