/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.DriverManagerDataSource;
import com.mchange.v2.c3p0.impl.C3P0JavaBeanObjectFactory;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class C3P0JavaBeanObjectFactoryTest {
    @Test
    void jndiObjectFactoryReconstructsAUsableDataSource() throws Exception {
        DriverManagerDataSource original = C3p0Support.unpooled();
        original.setDescription("JNDI reporting source");
        C3P0JavaBeanObjectFactory factory = new C3P0JavaBeanObjectFactory();
        DriverManagerDataSource restored = (DriverManagerDataSource) factory.getObjectInstance(
                        original.getReference(), null, null, null);
        assertNotSame(original, restored);
        assertEquals(original.getJdbcUrl(), restored.getJdbcUrl());
        assertEquals(original.getDescription(), restored.getDescription());
        assertEquals("sa", restored.getUser());
        try (Connection connection = restored.getConnection()) {
            C3p0Support.assertQuery(connection, 60);
        }
    }
}
