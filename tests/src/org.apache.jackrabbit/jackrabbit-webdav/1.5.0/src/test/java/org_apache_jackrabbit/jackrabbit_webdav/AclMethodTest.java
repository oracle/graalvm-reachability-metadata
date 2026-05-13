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

import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.AclMethod;
import org.apache.jackrabbit.webdav.security.AclProperty;
import org.junit.jupiter.api.Test;

public class AclMethodTest {
    @Test
    public void constructorCreatesAclRequestWithSerializedAclBody() throws Exception {
        ExposedAclMethod method = new ExposedAclMethod(
                "http://localhost:8080/repository/document.txt",
                new AclProperty(new AclProperty.Ace[0]));

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_ACL);
        assertThat(method.getPath()).isEqualTo("/repository/document.txt");
        assertThat(method.isSuccessfulStatus(200)).isTrue();
        assertThat(method.isSuccessfulStatus(207)).isFalse();

        RequestEntity requestEntity = method.getRequestEntity();
        assertThat(requestEntity).isNotNull();
        assertThat(requestEntity.isRepeatable()).isTrue();
        assertThat(requestEntity.getContentType()).startsWith("text/xml; charset=");

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        requestEntity.writeRequest(body);
        String xml = body.toString(StandardCharsets.UTF_8.name());

        assertThat(xml).contains("acl");
        assertThat(xml).contains("DAV:");
    }

    private static final class ExposedAclMethod extends AclMethod {
        private ExposedAclMethod(String uri, AclProperty aclProperty) throws Exception {
            super(uri, aclProperty);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
