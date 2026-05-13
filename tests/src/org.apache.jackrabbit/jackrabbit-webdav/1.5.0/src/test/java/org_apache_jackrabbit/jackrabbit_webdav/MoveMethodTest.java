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
import org.apache.jackrabbit.webdav.client.methods.MoveMethod;
import org.apache.jackrabbit.webdav.header.OverwriteHeader;
import org.junit.jupiter.api.Test;

public class MoveMethodTest {
    @Test
    public void constructorCreatesMoveRequest() {
        ExposedMoveMethod method = new ExposedMoveMethod(
                "http://localhost:8080/repository/source.txt",
                "http://localhost:8080/repository/target.txt",
                true);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_MOVE);
        assertThat(method.getPath()).isEqualTo("/repository/source.txt");
        assertThat(method.getRequestHeader(DavConstants.HEADER_DESTINATION).getValue())
                .isEqualTo("http://localhost:8080/repository/target.txt");
        assertThat(method.getRequestHeader(DavConstants.HEADER_OVERWRITE).getValue())
                .isEqualTo(OverwriteHeader.OVERWRITE_TRUE);
        assertThat(method.isSuccessfulStatus(201)).isTrue();
        assertThat(method.isSuccessfulStatus(204)).isTrue();
        assertThat(method.isSuccessfulStatus(200)).isFalse();
    }

    @Test
    public void constructorCanDisableOverwrite() {
        MoveMethod method = new MoveMethod(
                "http://localhost:8080/repository/source.txt",
                "http://localhost:8080/repository/existing.txt",
                false);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_MOVE);
        assertThat(method.getRequestHeader(DavConstants.HEADER_DESTINATION).getValue())
                .isEqualTo("http://localhost:8080/repository/existing.txt");
        assertThat(method.getRequestHeader(DavConstants.HEADER_OVERWRITE).getValue())
                .isEqualTo(OverwriteHeader.OVERWRITE_FALSE);
    }

    private static final class ExposedMoveMethod extends MoveMethod {
        private ExposedMoveMethod(String uri, String destinationUri, boolean overwrite) {
            super(uri, destinationUri, overwrite);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
