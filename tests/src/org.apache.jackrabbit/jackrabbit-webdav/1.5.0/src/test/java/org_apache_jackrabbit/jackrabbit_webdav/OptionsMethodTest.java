/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.OptionsMethod;
import org.junit.jupiter.api.Test;

public class OptionsMethodTest {
    @Test
    public void constructorCreatesOptionsRequest() {
        ExposedOptionsMethod method = new ExposedOptionsMethod("http://localhost:8080/repository/default");

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_OPTIONS);
        assertThat(method.getPath()).isEqualTo("/repository/default");
        assertThat(method.isSuccessfulStatus(200)).isTrue();
        assertThat(method.isSuccessfulStatus(201)).isFalse();
        assertThat(method.isSuccessfulStatus(204)).isFalse();
    }

    private static final class ExposedOptionsMethod extends OptionsMethod {
        private ExposedOptionsMethod(String uri) {
            super(uri);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
