package org_hibernate.hibernate_core;

public class MySQLDialectHibernateTest extends AbstractHibernateTest {

    @Override
    protected String getJdbcUrl() {
        return "jdbc:h2:mem:test;MODE=MYSQL";
    }

    @Override
    protected String getHibernateDialect() {
        return "org.hibernate.dialect.MySQL8Dialect";
    }
}
