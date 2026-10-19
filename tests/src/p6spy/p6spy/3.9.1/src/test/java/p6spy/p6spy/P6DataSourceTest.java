/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package p6spy.p6spy;

import static org.assertj.core.api.Assertions.assertThat;

import com.p6spy.engine.common.P6LogQuery;
import com.p6spy.engine.spy.P6DataSource;
import com.p6spy.engine.spy.P6DriverManagerDataSource;
import com.p6spy.engine.spy.P6ModuleManager;
import com.p6spy.engine.spy.P6SpyLoadableOptions;
import com.p6spy.engine.spy.P6SpyOptions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

public class P6DataSourceTest {
    private static final P6DriverManagerDataSource DELEGATE = new P6DriverManagerDataSource();

    @Test
    void executesStatementsWithConfiguredSlf4jAppenderAndDefaultModules() throws Exception {
        String appenderProperty = "p6spy.config.appender";
        String jmxProperty = "p6spy.config.jmx";
        String originalAppender = System.getProperty(appenderProperty);
        String originalJmx = System.getProperty(jmxProperty);
        System.setProperty(appenderProperty, "com.p6spy.engine.spy.appender.Slf4JLogger");
        System.setProperty(jmxProperty, "false");

        try {
            P6ModuleManager.getInstance().reload();
            P6SpyLoadableOptions options = P6SpyOptions.getActiveInstance();

            assertThat(options.getLogMessageFormatInstance()
                            .formatMessage(
                                    7,
                                    "now",
                                    3,
                                    "statement",
                                    "select 1",
                                    "select 1",
                                    "jdbc:h2:mem:p6spy"))
                    .isEqualTo(
                            "now|3|statement|connection 7|url jdbc:h2:mem:p6spy|select 1|select 1");
            assertThat(options.getDatabaseDialectBinaryFormatInstance().toString(new byte[] {0, 15}))
                    .isEqualTo("'000F'");
            assertThat(P6ModuleManager.getInstance().getFactories())
                    .extracting(factory -> factory.getClass().getName())
                    .contains(
                            "com.p6spy.engine.spy.P6SpyFactory",
                            "com.p6spy.engine.logging.P6LogFactory");
            assertThat(P6LogQuery.getLogger().getClass().getName())
                    .isEqualTo("com.p6spy.engine.spy.appender.Slf4JLogger");

            JdbcDataSource delegate = new JdbcDataSource();
            delegate.setURL("jdbc:h2:mem:p6spy");
            P6DataSource dataSource = new P6DataSource(delegate);
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("create table messages (id integer primary key, body varchar(32))");
                assertThat(statement.executeUpdate("insert into messages values (1, 'logged')"))
                        .isEqualTo(1);
                try (ResultSet resultSet =
                        statement.executeQuery("select body from messages where id = 1")) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString(1)).isEqualTo("logged");
                    assertThat(resultSet.next()).isFalse();
                }
            }
        } finally {
            restoreProperty(appenderProperty, originalAppender);
            restoreProperty(jmxProperty, originalJmx);
            P6ModuleManager.getInstance().reload();
        }
    }

    @Test
    void bindsJndiDataSourceAndAppliesStringAndIntegerProperties() throws Exception {
        P6SpyLoadableOptions options = P6SpyOptions.getActiveInstance();
        int originalLoginTimeout = DriverManager.getLoginTimeout();
        options.setJNDIContextFactory(TestInitialContextFactory.class.getName());
        options.setRealDataSourceProperties("user;alice,loginTimeout;11");
        P6DataSource dataSource = new P6DataSource();
        dataSource.setRealDataSource("jdbc/example");

        try {
            assertThat(dataSource.getLoginTimeout()).isEqualTo(11);
            assertThat(DELEGATE.getUser()).isEqualTo("alice");
        } finally {
            DriverManager.setLoginTimeout(originalLoginTimeout);
            options.unSetJNDIContextFactory();
            options.unSetRealDataSourceProperties();
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    public static class TestInitialContextFactory implements InitialContextFactory {
        @Override
        public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {
            return new TestInitialContext();
        }
    }

    public static class TestInitialContext extends InitialContext {
        public TestInitialContext() throws NamingException {
            super(true);
        }

        @Override
        public Object lookup(String name) {
            return DELEGATE;
        }
    }
}
