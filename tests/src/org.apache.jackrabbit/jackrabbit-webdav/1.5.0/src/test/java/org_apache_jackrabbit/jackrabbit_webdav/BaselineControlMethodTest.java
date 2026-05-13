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

import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.BaselineControlMethod;
import org.junit.jupiter.api.Test;

public class BaselineControlMethodTest {
    @Test
    public void constructorCreatesBaselineControlRequestWithBaselineHref() throws Exception {
        ExposedBaselineControlMethod method = new ExposedBaselineControlMethod(
                "http://localhost:8080/repository/versionable-node",
                "/repository/baselines/baseline-1");

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_BASELINE_CONTROL);
        assertThat(method.getPath()).isEqualTo("/repository/versionable-node");
        assertThat(method.isSuccessfulStatus(200)).isTrue();
        assertThat(method.isSuccessfulStatus(201)).isFalse();

        RequestEntity requestEntity = method.getRequestEntity();
        assertThat(requestEntity).isNotNull();
        assertThat(requestEntity.isRepeatable()).isTrue();
        assertThat(requestEntity.getContentType()).startsWith("text/xml; charset=");

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        requestEntity.writeRequest(body);
        String xml = body.toString(StandardCharsets.UTF_8.name());

        assertThat(xml).contains("baseline-control");
        assertThat(xml).contains("href");
        assertThat(xml).contains("/repository/baselines/baseline-1");
        assertThat(xml).contains("DAV:");
    }

    @Test
    public void constructorWithoutBaselineHrefKeepsRequestBodyEmpty() throws Exception {
        ExposedBaselineControlMethod method = new ExposedBaselineControlMethod(
                "http://localhost:8080/repository/versionable-node",
                null);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_BASELINE_CONTROL);
        assertThat(method.getPath()).isEqualTo("/repository/versionable-node");
        assertThat(method.getRequestEntity()).isNull();
    }

    private static final class ExposedBaselineControlMethod extends BaselineControlMethod {
        private ExposedBaselineControlMethod(String uri, String baselineHref) throws IOException {
            super(uri, baselineHref);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
