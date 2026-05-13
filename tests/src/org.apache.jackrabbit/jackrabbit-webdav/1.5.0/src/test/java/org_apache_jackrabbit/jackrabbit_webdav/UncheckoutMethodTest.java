/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.UncheckoutMethod;
import org.junit.jupiter.api.Test;

public class UncheckoutMethodTest {
    @Test
    public void constructorCreatesUncheckoutRequest() {
        ExposedUncheckoutMethod method = new ExposedUncheckoutMethod("http://localhost:8080/repository/document.txt");

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_UNCHECKOUT);
        assertThat(method.getPath()).isEqualTo("/repository/document.txt");
        assertThat(method.isSuccessfulStatus(200)).isTrue();
        assertThat(method.isSuccessfulStatus(201)).isFalse();
        assertThat(method.isSuccessfulStatus(204)).isFalse();
    }

    private static final class ExposedUncheckoutMethod extends UncheckoutMethod {
        private ExposedUncheckoutMethod(String uri) {
            super(uri);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
