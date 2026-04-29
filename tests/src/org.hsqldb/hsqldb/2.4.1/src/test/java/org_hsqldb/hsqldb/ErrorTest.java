/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import org.hsqldb.HsqlException;
import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.junit.jupiter.api.Test;

public class ErrorTest {
    private static final String INSUFFICIENT_PRIVILEGE_STATE = "42501";
    private static final String CUSTOM_MESSAGE = "custom authorization failure";

    @Test
    public void resolvesKnownSqlStateToHsqldbErrorCode() {
        int errorCode = Error.getCode(INSUFFICIENT_PRIVILEGE_STATE);

        assertThat(errorCode).isEqualTo(ErrorCode.X_42501);
    }

    @Test
    public void createsSignalExceptionForKnownSqlState() {
        HsqlException exception = Error.error(CUSTOM_MESSAGE, INSUFFICIENT_PRIVILEGE_STATE);

        assertThat(exception.getMessage()).isEqualTo(CUSTOM_MESSAGE);
        assertThat(exception.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE_STATE);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.X_42501);
    }
}
