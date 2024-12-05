/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

public class OracleDialectHibernateTest extends AbstractHibernateTest {

    @Override
    protected String getJdbcUrl() {
        return "jdbc:h2:mem:test;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH";
    }

    @Override
    protected String getHibernateDialect() {
        return "org.hibernate.dialect.OracleDialect";
    }
}
