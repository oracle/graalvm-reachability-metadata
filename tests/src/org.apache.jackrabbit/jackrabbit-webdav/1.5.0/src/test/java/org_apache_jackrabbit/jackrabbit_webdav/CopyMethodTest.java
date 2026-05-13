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
import org.apache.jackrabbit.webdav.client.methods.CopyMethod;
import org.apache.jackrabbit.webdav.header.OverwriteHeader;
import org.junit.jupiter.api.Test;

public class CopyMethodTest {
    @Test
    public void constructorCreatesDeepCopyRequest() {
        ExposedCopyMethod method = new ExposedCopyMethod(
                "http://localhost:8080/repository/source.txt",
                "http://localhost:8080/repository/target.txt",
                true);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_COPY);
        assertThat(method.getPath()).isEqualTo("/repository/source.txt");
        assertThat(method.getRequestHeader(DavConstants.HEADER_DESTINATION).getValue())
                .isEqualTo("http://localhost:8080/repository/target.txt");
        assertThat(method.getRequestHeader(DavConstants.HEADER_OVERWRITE).getValue())
                .isEqualTo(OverwriteHeader.OVERWRITE_TRUE);
        assertThat(method.getRequestHeader(DavConstants.HEADER_DEPTH)).isNull();
        assertThat(method.isSuccessfulStatus(201)).isTrue();
        assertThat(method.isSuccessfulStatus(204)).isTrue();
        assertThat(method.isSuccessfulStatus(200)).isFalse();
    }

    @Test
    public void constructorCreatesShallowCopyRequest() {
        ExposedCopyMethod method = new ExposedCopyMethod(
                "http://localhost:8080/repository/collection",
                "http://localhost:8080/repository/copy",
                false,
                true);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_COPY);
        assertThat(method.getRequestHeader(DavConstants.HEADER_DESTINATION).getValue())
                .isEqualTo("http://localhost:8080/repository/copy");
        assertThat(method.getRequestHeader(DavConstants.HEADER_OVERWRITE).getValue())
                .isEqualTo(OverwriteHeader.OVERWRITE_FALSE);
        assertThat(method.getRequestHeader(DavConstants.HEADER_DEPTH).getValue()).isEqualTo("0");
    }

    private static final class ExposedCopyMethod extends CopyMethod {
        private ExposedCopyMethod(String uri, String destinationUri, boolean overwrite) {
            super(uri, destinationUri, overwrite);
        }

        private ExposedCopyMethod(String uri, String destinationUri, boolean overwrite, boolean shallow) {
            super(uri, destinationUri, overwrite, shallow);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
