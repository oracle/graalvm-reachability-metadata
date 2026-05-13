/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.LabelMethod;
import org.apache.jackrabbit.webdav.version.LabelInfo;
import org.junit.jupiter.api.Test;

public class LabelMethodTest {
    @Test
    public void constructorCreatesLabelRequestWithSerializedLabelBody() throws Exception {
        ExposedLabelMethod method = new ExposedLabelMethod(
                "http://localhost:8080/repository/versionable-node",
                "release-candidate",
                LabelInfo.TYPE_ADD,
                DavConstants.DEPTH_INFINITY);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_LABEL);
        assertThat(method.getPath()).isEqualTo("/repository/versionable-node");
        assertThat(method.isSuccessfulStatus(200)).isTrue();
        assertThat(method.isSuccessfulStatus(201)).isFalse();

        Header depthHeader = method.getRequestHeader(DavConstants.HEADER_DEPTH);
        assertThat(depthHeader).isNotNull();
        assertThat(depthHeader.getValue()).isEqualTo(DavConstants.DEPTH_INFINITY_S);

        RequestEntity requestEntity = method.getRequestEntity();
        assertThat(requestEntity).isNotNull();
        assertThat(requestEntity.isRepeatable()).isTrue();
        assertThat(requestEntity.getContentType()).startsWith("text/xml; charset=");

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        requestEntity.writeRequest(body);
        String xml = body.toString(StandardCharsets.UTF_8.name());

        assertThat(xml).contains("label");
        assertThat(xml).contains("add");
        assertThat(xml).contains("label-name");
        assertThat(xml).contains("release-candidate");
        assertThat(xml).contains("DAV:");
    }

    private static final class ExposedLabelMethod extends LabelMethod {
        private ExposedLabelMethod(String uri, String label, int type, int depth) throws Exception {
            super(uri, label, type, depth);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
