/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.debug.AfterCloseLoggingConnectionWrapper;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

public class AfterCloseLoggingConnectionWrapperTest {
    @Test
    void wrapsAndDelegatesConnectionCalls() throws Exception {
        try (Connection raw = DriverManager.getConnection(C3p0TestSupport.jdbcUrl("after-close"), C3p0TestSupport.USER, C3p0TestSupport.PASSWORD)) {
            Connection wrapped = AfterCloseLoggingConnectionWrapper.wrap(raw);

            assertThat(wrapped.getAutoCommit()).isTrue();
            wrapped.close();
            assertThat(wrapped.isClosed()).isTrue();
        }
    }
}
