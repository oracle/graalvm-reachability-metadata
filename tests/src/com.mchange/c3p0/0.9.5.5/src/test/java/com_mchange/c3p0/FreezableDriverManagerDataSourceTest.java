/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.test.FreezableDriverManagerDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.util.UUID;

public class FreezableDriverManagerDataSourceTest {
    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void loadsTheConfiguredDriverForConnections() throws Exception {
        FreezableDriverManagerDataSource source = new FreezableDriverManagerDataSource(false);
        source.setIdentityToken(UUID.randomUUID().toString());
        source.setDriverClass("org.h2.Driver");
        source.setJdbcUrl(C3p0Support.databaseUrl());
        source.setUser("sa");
        source.setPassword("");
        try (Connection connection = source.getConnection()) {
            C3p0Support.assertQuery(connection, 66);
        }
        try (Connection connection = source.getConnection("sa", "")) {
            C3p0Support.assertQuery(connection, 67);
        }
    }
}
