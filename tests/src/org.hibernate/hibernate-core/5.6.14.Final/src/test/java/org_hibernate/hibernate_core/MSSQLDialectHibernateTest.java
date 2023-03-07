package org_hibernate.hibernate_core;

public class MSSQLDialectHibernateTest extends AbstractHibernateTest {

    @Override
    protected String getJdbcUrl() {
        return "jdbc:h2:mem:test;MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE";
    }

    @Override
    protected String getHibernateDialect() {
        return "org.hibernate.dialect.SQLServer2012Dialect";
    }
}
