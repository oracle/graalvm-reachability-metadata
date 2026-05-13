/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.MergeMethod;
import org.apache.jackrabbit.webdav.version.MergeInfo;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

public class MergeMethodTest {
    @Test
    public void constructorCreatesMergeRequestWithMergeInfoBody() throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        MergeInfo mergeInfo = new MergeInfo(MergeInfo.createMergeElement(
                new String[] {"/repository/workspace/source"}, true, true, document));

        ExposedMergeMethod method = new ExposedMergeMethod(
                "http://localhost:8080/repository/workspace/target",
                mergeInfo);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_MERGE);
        assertThat(method.getPath()).isEqualTo("/repository/workspace/target");
        assertThat(method.isSuccessfulStatus(207)).isTrue();
        assertThat(method.isSuccessfulStatus(200)).isFalse();
        assertThat(method.isSuccessfulStatus(201)).isFalse();

        RequestEntity requestEntity = method.getRequestEntity();
        assertThat(requestEntity).isNotNull();
        assertThat(requestEntity.isRepeatable()).isTrue();
        assertThat(requestEntity.getContentType()).startsWith("text/xml; charset=");

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        requestEntity.writeRequest(body);
        String xml = body.toString(StandardCharsets.UTF_8.name());

        assertThat(xml).contains("merge");
        assertThat(xml).contains("source");
        assertThat(xml).contains("href");
        assertThat(xml).contains("/repository/workspace/source");
        assertThat(xml).contains("no-auto-merge");
        assertThat(xml).contains("no-checkout");
    }

    private static final class ExposedMergeMethod extends MergeMethod {
        private ExposedMergeMethod(String uri, MergeInfo mergeInfo) throws IOException {
            super(uri, mergeInfo);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
