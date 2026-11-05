/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.DriverManagerDataSource;
import com.mchange.v2.c3p0.util.TestUtils;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class TestUtilsTest {
    @Test
    void unreliableDataSourcePreservesDataSourceConfigurationAccess() throws Exception {
        DriverManagerDataSource source = C3p0Support.unpooled();
        DataSource wrapped = TestUtils.unreliableCommitDataSource(source);
        assertNotSame(source, wrapped);
        assertEquals(source.getLoginTimeout(), wrapped.getLoginTimeout());
        assertEquals(source.getParentLogger(), wrapped.getParentLogger());
    }
}
