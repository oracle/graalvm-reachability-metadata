/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.MkColMethod;
import org.junit.jupiter.api.Test;

public class MkColMethodTest {
    @Test
    public void constructorCreatesMkColRequest() {
        ExposedMkColMethod method = new ExposedMkColMethod("http://localhost:8080/repository/new-collection");

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_MKCOL);
        assertThat(method.getPath()).isEqualTo("/repository/new-collection");
        assertThat(method.isSuccessfulStatus(201)).isTrue();
        assertThat(method.isSuccessfulStatus(200)).isFalse();
        assertThat(method.isSuccessfulStatus(204)).isFalse();
    }

    private static final class ExposedMkColMethod extends MkColMethod {
        private ExposedMkColMethod(String uri) {
            super(uri);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
