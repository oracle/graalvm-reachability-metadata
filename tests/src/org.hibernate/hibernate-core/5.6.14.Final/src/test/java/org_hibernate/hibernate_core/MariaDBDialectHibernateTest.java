package org_hibernate.hibernate_core;

public class MariaDBDialectHibernateTest extends AbstractHibernateTest {

    @Override
    protected String getJdbcUrl() {
        // use MYSQL mode instead of MariaDB which fails in h2
        // when parsing `engine=InnoDB` in create table statement
        return "jdbc:h2:mem:test;MODE=MYSQL";
    }

    @Override
    protected String getHibernateDialect() {
        return "org.hibernate.dialect.MariaDB103Dialect";
    }
}
