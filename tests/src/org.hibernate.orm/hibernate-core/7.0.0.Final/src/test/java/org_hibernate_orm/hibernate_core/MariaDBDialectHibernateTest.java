/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

public class MariaDBDialectHibernateTest extends AbstractHibernateTest {

    @Override
    protected String getJdbcUrl() {
        // use MYSQL mode instead of MariaDB which fails in h2
        // when parsing `engine=InnoDB` in create table statement
        return "jdbc:h2:mem:test;MODE=MYSQL";
    }

    @Override
    protected String getHibernateDialect() {
        return "org.hibernate.dialect.MariaDBDialect";
    }
}
