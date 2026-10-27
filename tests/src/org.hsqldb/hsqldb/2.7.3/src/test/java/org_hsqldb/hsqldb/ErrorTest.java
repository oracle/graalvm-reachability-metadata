/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.junit.jupiter.api.Test;

public class ErrorTest {
    @Test
    void resolvesKnownSqlStateToErrorCode() {
        assertThat(Error.getCode("45000")).isEqualTo(ErrorCode.X_45000);
    }
}
