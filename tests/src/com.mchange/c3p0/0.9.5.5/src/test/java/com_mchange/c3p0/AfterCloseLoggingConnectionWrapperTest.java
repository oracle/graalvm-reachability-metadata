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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class AfterCloseLoggingConnectionWrapperTest {
    @Test
    void wrapsAConnectionWithoutChangingQueryResults() throws Exception {
        try (Connection physical = C3p0Support.unpooled().getConnection();
                        Connection wrapped = AfterCloseLoggingConnectionWrapper.wrap(physical)) {
            assertNotSame(physical, wrapped);
            assertFalse(wrapped.isClosed());
            C3p0Support.assertQuery(wrapped, 65);
        }
    }
}
