/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.support.JdbcUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JdbcUtilsAnonymous1Test {
    @Test
    void extractsDatabaseMetadataByMethodName() throws Exception {
        assertEquals("H2", JdbcUtils.extractDatabaseMetaData(TestDatabase.source(), "getDatabaseProductName"));
        assertEquals(Boolean.TRUE, JdbcUtils.extractDatabaseMetaData(TestDatabase.source(), "supportsTransactions"));
    }
}
