package org_hibernate.hibernate_core;

public class MariaDBDialectHibernateTest extends AbstractHibernateTest {

    @Override
    protected String getJdbcUrl() {
        return "jdbc:h2:mem:test;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
    }

    @Override
    protected String getHibernateDialect() {
        return "org.hibernate.dialect.MariaDB103Dialect";
    }
}
