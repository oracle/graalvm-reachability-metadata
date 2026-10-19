/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package p6spy.p6spy;

import static org.assertj.core.api.Assertions.assertThat;

import com.p6spy.engine.spy.P6DataSource;
import com.p6spy.engine.spy.P6DriverManagerDataSource;
import com.p6spy.engine.spy.P6SpyLoadableOptions;
import com.p6spy.engine.spy.P6SpyOptions;
import java.sql.DriverManager;
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import org.junit.jupiter.api.Test;

public class P6DataSourceTest {
    private static final P6DriverManagerDataSource DELEGATE = new P6DriverManagerDataSource();

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
