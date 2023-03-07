package org_hibernate.hibernate_core;

public class PostgresDialectHibernateTest  extends AbstractHibernateTest {

    @Override
    protected String getJdbcUrl() {
        return "jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
    }

    @Override
    protected String getHibernateDialect() {
        return "org.hibernate.dialect.PostgreSQL95Dialect";
    }
}
