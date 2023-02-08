/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.Constants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings("ObviousNullCheck")
public class TestConstants {

    @Test
    public void testConstants() {
        assertNotNull(new Constants());
        assertEquals(",connectionpool=", Constants.JMX_CONNECTION_POOL_BASE_EXT);
        assertEquals("connections", Constants.JMX_CONNECTION_POOL_PREFIX);
        assertEquals(",connectionpool=connections,connection=", Constants.JMX_CONNECTION_BASE_EXT);
        assertEquals(",connectionpool=connections,connection=", Constants.JMX_STATEMENT_POOL_BASE_EXT);
        assertEquals(",statementpool=statements", Constants.JMX_STATEMENT_POOL_PREFIX);
    }
}
