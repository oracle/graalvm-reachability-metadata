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
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.LockMethod;
import org.apache.jackrabbit.webdav.lock.Scope;
import org.apache.jackrabbit.webdav.lock.Type;
import org.junit.jupiter.api.Test;

public class LockMethodTest {
    @Test
    public void constructorCreatesLockRequestWithHeadersAndBody() throws Exception {
        ExposedLockMethod method = new ExposedLockMethod(
                "http://localhost:8080/repository/document.txt",
                Scope.EXCLUSIVE,
                Type.WRITE,
                "integration-test-owner",
                120000L,
                true);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_LOCK);
        assertThat(method.getPath()).isEqualTo("/repository/document.txt");
        assertThat(method.isSuccessfulStatus(200)).isTrue();
        assertThat(method.isSuccessfulStatus(201)).isFalse();
        assertThat(method.getRequestHeader(DavConstants.HEADER_TIMEOUT).getValue()).isEqualTo("Second-120");
        assertThat(method.getRequestHeader(DavConstants.HEADER_DEPTH).getValue())
                .isEqualTo(DavConstants.DEPTH_INFINITY_S);

        RequestEntity requestEntity = method.getRequestEntity();
        assertThat(requestEntity).isNotNull();
        assertThat(requestEntity.isRepeatable()).isTrue();
        assertThat(requestEntity.getContentType()).startsWith("text/xml; charset=");

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        requestEntity.writeRequest(body);
        String xml = body.toString(StandardCharsets.UTF_8.name());

        assertThat(xml).contains("lockinfo");
        assertThat(xml).contains("exclusive");
        assertThat(xml).contains("write");
        assertThat(xml).contains("integration-test-owner");
    }

    @Test
    public void refreshConstructorCreatesLockRequestWithIfHeaderAndNoBody() {
        ExposedLockMethod method = new ExposedLockMethod(
                "http://localhost:8080/repository/document.txt",
                60000L,
                new String[] {"opaquelocktoken:test-token"});

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_LOCK);
        assertThat(method.getPath()).isEqualTo("/repository/document.txt");
        assertThat(method.getRequestHeader(DavConstants.HEADER_TIMEOUT).getValue()).isEqualTo("Second-60");
        assertThat(method.getRequestHeader(DavConstants.HEADER_IF).getValue())
                .isEqualTo("(<opaquelocktoken:test-token>)");
        assertThat(method.getRequestEntity()).isNull();
    }

    private static final class ExposedLockMethod extends LockMethod {
        private ExposedLockMethod(String uri, Scope lockScope, Type lockType, String owner,
                long timeout, boolean isDeep) throws Exception {
            super(uri, lockScope, lockType, owner, timeout, isDeep);
        }

        private ExposedLockMethod(String uri, long timeout, String[] lockTokens) {
            super(uri, timeout, lockTokens);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
