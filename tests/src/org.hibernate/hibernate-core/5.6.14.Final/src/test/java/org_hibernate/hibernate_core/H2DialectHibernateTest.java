package org_hibernate.hibernate_core;

public class H2DialectHibernateTest extends AbstractHibernateTest {

    @Override
    protected String getJdbcUrl() {
        return "jdbc:h2:mem:test";
    }

    @Override
    protected String getHibernateDialect() {
        return "org.hibernate.dialect.H2Dialect";
    }
}
