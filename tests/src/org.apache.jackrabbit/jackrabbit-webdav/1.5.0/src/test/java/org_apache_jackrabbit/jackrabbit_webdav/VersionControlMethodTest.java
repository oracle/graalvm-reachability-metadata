/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.VersionControlMethod;
import org.junit.jupiter.api.Test;

public class VersionControlMethodTest {
    @Test
    public void constructorCreatesVersionControlRequest() {
        ExposedVersionControlMethod method = new ExposedVersionControlMethod(
                "http://localhost:8080/repository/versionable-node");

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_VERSION_CONTROL);
        assertThat(method.getPath()).isEqualTo("/repository/versionable-node");
        assertThat(method.isSuccessfulStatus(200)).isTrue();
        assertThat(method.isSuccessfulStatus(201)).isFalse();
        assertThat(method.isSuccessfulStatus(204)).isFalse();
    }

    private static final class ExposedVersionControlMethod extends VersionControlMethod {
        private ExposedVersionControlMethod(String uri) {
            super(uri);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
