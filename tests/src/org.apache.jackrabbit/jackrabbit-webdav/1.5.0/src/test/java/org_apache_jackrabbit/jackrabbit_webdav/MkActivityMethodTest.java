/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.MkActivityMethod;
import org.junit.jupiter.api.Test;

public class MkActivityMethodTest {
    @Test
    public void constructorCreatesMkActivityRequest() {
        ExposedMkActivityMethod method = new ExposedMkActivityMethod("http://localhost:8080/repository/activities/activity-1");

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_MKACTIVITY);
        assertThat(method.getPath()).isEqualTo("/repository/activities/activity-1");
        assertThat(method.isSuccessfulStatus(201)).isTrue();
        assertThat(method.isSuccessfulStatus(200)).isFalse();
        assertThat(method.isSuccessfulStatus(204)).isFalse();
    }

    private static final class ExposedMkActivityMethod extends MkActivityMethod {
        private ExposedMkActivityMethod(String uri) {
            super(uri);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
