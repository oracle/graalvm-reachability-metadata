/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.DriverManagerDataSource;
import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.cfg.MultiPropertiesConfig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class C3p0Support {
    private C3p0Support() {
    }

    static String databaseUrl() {
        return "jdbc:h2:mem:c3p0_" + UUID.randomUUID().toString().replace("-", "");
    }

    static DriverManagerDataSource unpooled() {
        DriverManagerDataSource source = new DriverManagerDataSource(false);
        source.setIdentityToken(UUID.randomUUID().toString());
        source.setDriverClass("org.h2.Driver");
        source.setJdbcUrl(databaseUrl());
        source.setUser("sa");
        source.setPassword("");
        return source;
    }

    static WrapperConnectionPoolDataSource wrapper(boolean traditional) throws Exception {
        WrapperConnectionPoolDataSource source = new WrapperConnectionPoolDataSource(false);
        source.setIdentityToken(UUID.randomUUID().toString());
        source.setNestedDataSource(unpooled());
        source.setUsesTraditionalReflectiveProxies(traditional);
        return source;
    }

    static ComboPooledDataSource pool() throws Exception {
        ComboPooledDataSource source = new ComboPooledDataSource();
        source.setDriverClass("org.h2.Driver");
        source.setJdbcUrl(databaseUrl());
        source.setUser("sa");
        source.setPassword("");
        source.setMinPoolSize(1);
        source.setInitialPoolSize(1);
        source.setMaxPoolSize(1);
        source.setAcquireIncrement(1);
        source.setAcquireRetryAttempts(1);
        source.setCheckoutTimeout(30000);
        source.setForceSynchronousCheckins(true);
        return source;
    }

    static void assertQuery(Connection connection, int expected) throws Exception {
        try (Statement statement = connection.createStatement();
                        ResultSet rows = statement.executeQuery("SELECT " + expected)) {
            assertTrue(rows.next());
            assertEquals(expected, rows.getInt(1));
            assertFalse(rows.next());
        }
    }

    static <T extends Serializable> T roundTrip(T value, Class<T> type) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return type.cast(input.readObject());
        }
    }

    static AutoCloseable configuration(String key, String value) {
        Properties properties = new Properties();
        properties.setProperty(key, value);
        C3P0Config.refreshMainConfig(new MultiPropertiesConfig[]{MultiPropertiesConfig.fromProperties(properties)},
                        "coordinate test configuration");
        return C3P0Config::refreshMainConfig;
    }
}
