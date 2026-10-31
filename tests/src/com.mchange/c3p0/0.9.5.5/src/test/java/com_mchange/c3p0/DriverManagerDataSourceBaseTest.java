/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.DriverManagerDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class DriverManagerDataSourceBaseTest {
    @Test
    void serializedDataSourceRetainsConnectionConfiguration() throws Exception {
        DriverManagerDataSource original = C3p0Support.unpooled();
        original.setDescription("reporting database");
        original.setForceUseNamedDriverClass(true);
        DriverManagerDataSource restored = C3p0Support.roundTrip(original, DriverManagerDataSource.class);
        assertNotSame(original, restored);
        assertEquals(original.getJdbcUrl(), restored.getJdbcUrl());
        assertEquals("reporting database", restored.getDescription());
        assertEquals("sa", restored.getUser());
        assertEquals("", restored.getPassword());
        try (Connection connection = restored.getConnection()) {
            C3p0Support.assertQuery(connection, 43);
        }
    }
}
