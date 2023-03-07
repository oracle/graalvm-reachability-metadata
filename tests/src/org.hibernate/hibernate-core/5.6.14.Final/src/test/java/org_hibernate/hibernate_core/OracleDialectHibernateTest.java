package org_hibernate.hibernate_core;

public class OracleDialectHibernateTest extends AbstractHibernateTest {

    @Override
    protected String getJdbcUrl() {
        return "jdbc:h2:mem:test;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH";
    }

    @Override
    protected String getHibernateDialect() {
        return "org.hibernate.dialect.Oracle10gDialect";
    }
}
