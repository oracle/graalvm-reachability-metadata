/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.DriverManagerDataSource;
import com.mchange.v2.c3p0.PoolBackedDataSource;
import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import com.mchange.v2.c3p0.cfg.C3P0Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.UUID;

public final class C3p0TestSupport {
    public static final String H2_DRIVER = "org.h2.Driver";
    public static final String USER = "sa";
    public static final String PASSWORD = "";

    private C3p0TestSupport() {
    }

    public static String jdbcUrl(String name) {
        return "jdbc:h2:mem:"
            + name
            + "-"
            + UUID.randomUUID()
            + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }

    public static DriverManagerDataSource newDriverManagerDataSource(String name) throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClass(H2_DRIVER);
        dataSource.setJdbcUrl(jdbcUrl(name));
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        return dataSource;
    }

    public static WrapperConnectionPoolDataSource newWrapperConnectionPoolDataSource(String name, boolean traditionalProxies)
        throws Exception {
        WrapperConnectionPoolDataSource dataSource = new WrapperConnectionPoolDataSource();
        dataSource.setNestedDataSource(newDriverManagerDataSource(name));
        dataSource.setAcquireIncrement(1);
        dataSource.setInitialPoolSize(1);
        dataSource.setMaxPoolSize(2);
        dataSource.setMinPoolSize(1);
        dataSource.setUsesTraditionalReflectiveProxies(traditionalProxies);
        return dataSource;
    }

    public static PoolBackedDataSource newPoolBackedDataSource(String name, boolean traditionalProxies, int maxStatements)
        throws Exception {
        WrapperConnectionPoolDataSource cpds = newWrapperConnectionPoolDataSource(name, traditionalProxies);
        cpds.setMaxStatements(maxStatements);
        cpds.setMaxStatementsPerConnection(maxStatements);

        PoolBackedDataSource dataSource = new PoolBackedDataSource();
        dataSource.setConnectionPoolDataSource(cpds);
        dataSource.setDataSourceName(name);
        dataSource.setNumHelperThreads(1);
        return dataSource;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Serializable> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }

    public static void withRefreshedProperty(String key, String value, ThrowingRunnable action) throws Exception {
        String previous = System.getProperty(key);
        try {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
            C3P0Config.refreshMainConfig();
            action.run();
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
            C3P0Config.refreshMainConfig();
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
