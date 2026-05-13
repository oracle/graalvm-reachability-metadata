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
import org.apache.jackrabbit.webdav.client.methods.UpdateMethod;
import org.apache.jackrabbit.webdav.version.UpdateInfo;
import org.junit.jupiter.api.Test;

public class UpdateMethodTest {
    @Test
    public void constructorCreatesUpdateRequestWithUpdateInfoBody() throws Exception {
        UpdateInfo updateInfo = new UpdateInfo(
                new String[] {"/repository/workspace/version-history/version-1"},
                UpdateInfo.UPDATE_BY_VERSION,
                null);

        ExposedUpdateMethod method = new ExposedUpdateMethod(
                "http://localhost:8080/repository/workspace/document.txt",
                updateInfo);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_UPDATE);
        assertThat(method.getPath()).isEqualTo("/repository/workspace/document.txt");
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

        assertThat(xml).contains("update");
        assertThat(xml).contains("version");
        assertThat(xml).contains("href");
        assertThat(xml).contains("/repository/workspace/version-history/version-1");
    }

    private static final class ExposedUpdateMethod extends UpdateMethod {
        private ExposedUpdateMethod(String uri, UpdateInfo updateInfo) throws IOException {
            super(uri, updateInfo);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
