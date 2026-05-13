/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.CheckinMethod;
import org.junit.jupiter.api.Test;

public class CheckinMethodTest {
    @Test
    public void constructorCreatesCheckinRequest() {
        ExposedCheckinMethod method = new ExposedCheckinMethod("http://localhost:8080/repository/document.txt");

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_CHECKIN);
        assertThat(method.getPath()).isEqualTo("/repository/document.txt");
        assertThat(method.isSuccessfulStatus(201)).isTrue();
        assertThat(method.isSuccessfulStatus(200)).isFalse();
        assertThat(method.isSuccessfulStatus(204)).isFalse();
    }

    private static final class ExposedCheckinMethod extends CheckinMethod {
        private ExposedCheckinMethod(String uri) {
            super(uri);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
