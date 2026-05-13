/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.UnLockMethod;
import org.junit.jupiter.api.Test;

public class UnLockMethodTest {
    @Test
    public void constructorCreatesUnlockRequestWithLockTokenHeader() {
        ExposedUnLockMethod method = new ExposedUnLockMethod(
                "http://localhost:8080/repository/document.txt",
                "opaquelocktoken:test-token");

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_UNLOCK);
        assertThat(method.getPath()).isEqualTo("/repository/document.txt");
        assertThat(method.getRequestHeader(DavConstants.HEADER_LOCK_TOKEN).getValue())
                .isEqualTo("<opaquelocktoken:test-token>");
        assertThat(method.getRequestEntity()).isNull();
        assertThat(method.isSuccessfulStatus(204)).isTrue();
        assertThat(method.isSuccessfulStatus(200)).isFalse();
    }

    @Test
    public void constructorPreservesAlreadyCodedLockTokenHeader() {
        ExposedUnLockMethod method = new ExposedUnLockMethod(
                "http://localhost:8080/repository/document.txt",
                "<opaquelocktoken:test-token>");

        assertThat(method.getRequestHeader(DavConstants.HEADER_LOCK_TOKEN).getValue())
                .isEqualTo("<opaquelocktoken:test-token>");
    }

    private static final class ExposedUnLockMethod extends UnLockMethod {
        private ExposedUnLockMethod(String uri, String lockToken) {
            super(uri, lockToken);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
