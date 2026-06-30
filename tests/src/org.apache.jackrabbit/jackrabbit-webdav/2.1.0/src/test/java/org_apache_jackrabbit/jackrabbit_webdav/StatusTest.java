/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class StatusTest {
    @Test
    void parsesStatusLineAndExposesStatusCode() {
        Status status = Status.parse("HTTP/1.1 404 Not Found");

        assertThat(status.getStatusCode()).isEqualTo(404);
    }

    @Test
    void rejectsStatusLineWithoutNumericStatusCode() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Status.parse("HTTP/1.1 abc Not Found"))
                .withMessage("Unable to parse status code from status line: 'HTTP/1.1 abc Not Found'");
    }
}
