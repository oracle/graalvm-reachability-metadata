/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;
import javax.sql.DataSource;

import org.apache.naming.NamingContext;
import org.apache.naming.ResourceLinkRef;
import org.apache.naming.factory.DataSourceLinkFactory;
import org.apache.naming.factory.ResourceLinkFactory;
import org.h2.jdbcx.JdbcDataSource;

public final class DataSourceSupport implements AutoCloseable {
    private final NamingContext global = new NamingContext(null, "database");
    private final JdbcDataSource original;
    private final DataSource linked;

    public DataSourceSupport(String database) throws Exception {
        original = database(database);
        original.setUser("unused-default-user");
        global.bind("sharedDatabase", original);
        ResourceLinkFactory.setGlobalContext(global);
        ResourceLinkFactory.registerGlobalResourceAccess(global, "localDatabase", "sharedDatabase");
        ResourceLinkRef reference = new ResourceLinkRef(DataSource.class.getName(), "sharedDatabase", null, null);
        reference.add(new StringRefAddr("username", "reader"));
        reference.add(new StringRefAddr("password", "secret"));
        linked = (DataSource) new DataSourceLinkFactory().getObjectInstance(reference, null, null, null);
    }

    public DataSource linked() {
        return linked;
    }

    public DataSource original() {
        return original;
    }

    public static JdbcDataSource database(String name) {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + name);
        source.setUser("reader");
        source.setPassword("secret");
        return source;
    }

    @Override
    public void close() throws Exception {
        ResourceLinkFactory.deregisterGlobalResourceAccess(global);
        ResourceLinkFactory.setGlobalContext(null);
        global.close();
    }

    public static class Factory implements ObjectFactory {
        @Override
        public Object getObjectInstance(Object object, Name name, Context context, Hashtable<?, ?> environment) {
            return database("factory-database");
        }
    }
}
