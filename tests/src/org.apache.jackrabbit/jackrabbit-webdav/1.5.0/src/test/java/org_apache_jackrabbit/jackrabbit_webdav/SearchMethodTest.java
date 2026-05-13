/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.client.methods.SearchMethod;
import org.apache.jackrabbit.webdav.search.SearchInfo;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class SearchMethodTest {
    @Test
    public void constructorCreatesSearchRequestForStatementAndLanguage() throws Exception {
        ExposedSearchMethod method = new ExposedSearchMethod(
                "http://localhost:8080/repository/default",
                "//element(*, nt:file)",
                "xpath");

        Document requestBody = requestBody(method);
        Element searchRequest = requestBody.getDocumentElement();
        Element query = firstElementChild(searchRequest);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_SEARCH);
        assertThat(method.getPath()).isEqualTo("/repository/default");
        assertThat(method.isSuccessfulStatus(DavServletResponse.SC_MULTI_STATUS)).isTrue();
        assertThat(method.isSuccessfulStatus(200)).isFalse();
        assertThat(searchRequest.getLocalName()).isEqualTo("searchrequest");
        assertThat(searchRequest.getNamespaceURI()).isEqualTo("DAV:");
        assertThat(query.getLocalName()).isEqualTo("xpath");
        assertThat(query.getNamespaceURI()).isNullOrEmpty();
        assertThat(query.getTextContent()).isEqualTo("//element(*, nt:file)");
    }

    @Test
    public void searchInfoConstructorPreservesLanguageNamespaceAndQuery() throws Exception {
        Namespace languageNamespace = Namespace.getNamespace("jcr", "http://www.day.com/jcr/webdav/1.0");
        SearchInfo searchInfo = new SearchInfo("xpath", languageNamespace, "/jcr:root/content//*");
        ExposedSearchMethod method = new ExposedSearchMethod(
                "http://localhost:8080/repository/default/content",
                searchInfo);

        Document requestBody = requestBody(method);
        Element query = firstElementChild(requestBody.getDocumentElement());

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_SEARCH);
        assertThat(method.getPath()).isEqualTo("/repository/default/content");
        assertThat(query.getLocalName()).isEqualTo("xpath");
        assertThat(query.getNamespaceURI()).isEqualTo("http://www.day.com/jcr/webdav/1.0");
        assertThat(query.getTextContent()).isEqualTo("/jcr:root/content//*");
    }

    private static Document requestBody(SearchMethod method) throws Exception {
        RequestEntity requestEntity = method.getRequestEntity();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        requestEntity.writeRequest(output);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(output.toByteArray()));
    }

    private static Element firstElementChild(Element element) {
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) child;
            }
        }
        return null;
    }

    private static final class ExposedSearchMethod extends SearchMethod {
        private ExposedSearchMethod(String uri, String statement, String language) throws IOException {
            super(uri, statement, language);
        }

        private ExposedSearchMethod(String uri, SearchInfo searchInfo) throws IOException {
            super(uri, searchInfo);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
