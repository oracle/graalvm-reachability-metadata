/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2;

import org.apache.commons.dbcp2.datasources.PerUserPoolDataSource;
import org.apache.commons.dbcp2.datasources.SharedPoolDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.util.Hashtable;

import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("UnusedReturnValue")
public class TestJndi {
    protected static final String JNDI_SUBCONTEXT = "jdbc";
    protected static final String JNDI_PATH = JNDI_SUBCONTEXT + "/" + "jndiTestDataSource";
    protected Context context;

    protected void bindDataSource(final DataSource dataSource) throws Exception {
        context.bind(JNDI_PATH, dataSource);
    }

    protected void checkBind(final DataSource dataSource) throws Exception {
        bindDataSource(dataSource);
        retrieveDataSource();
    }

    protected InitialContext getInitialContext() throws NamingException {
        //Checkstyle: stop field name check
        final Hashtable<String, String> environment = new Hashtable<>();
        //Checkstyle: resume field name check
        environment.put(Context.INITIAL_CONTEXT_FACTORY,
                org.apache.naming.java.javaURLContextFactory.class.getName());
        return new InitialContext(environment);
    }

    protected DataSource retrieveDataSource() throws Exception {
        final Context ctx = getInitialContext();
        final DataSource dataSource = (DataSource) ctx.lookup(JNDI_PATH);
        if (dataSource == null) {
            fail("DataSource should not be null");
        }
        return dataSource;
    }

    @BeforeEach
    public void setUp() throws Exception {
        context = getInitialContext();
        context.createSubcontext(JNDI_SUBCONTEXT);
    }

    @AfterEach
    public void tearDown() throws Exception {
        context.unbind(JNDI_PATH);
        context.destroySubcontext(JNDI_SUBCONTEXT);
    }

    @Test
    public void testBasicDataSourceBind() throws Exception {
        final BasicDataSource dataSource = new BasicDataSource();
        checkBind(dataSource);
    }

    @Test
    public void testPerUserPoolDataSourceBind() throws Exception {
        final PerUserPoolDataSource dataSource = new PerUserPoolDataSource();
        checkBind(dataSource);
    }

    @Test
    public void testSharedPoolDataSourceBind() throws Exception {
        final SharedPoolDataSource dataSource = new SharedPoolDataSource();
        checkBind(dataSource);
    }
}
