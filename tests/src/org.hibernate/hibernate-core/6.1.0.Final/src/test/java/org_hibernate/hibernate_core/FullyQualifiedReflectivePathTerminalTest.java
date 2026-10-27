/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class FullyQualifiedReflectivePathTerminalTest extends AbstractHibernateTest {

    @Test
    public void evaluatesAQualifiedPublicConstantInAQuery() {
        List<String> values = executeQuery(
                "select org.hibernate.cfg.AvailableSettings.JAKARTA_JDBC_URL from Student",
                String.class
        );

        assertThat(values).hasSize(5).allMatch("jakarta.persistence.jdbc.url"::equals);
    }

    @Override
    protected String getJdbcUrl() {
        return "jdbc:h2:mem:static-field-qualified";
    }

    @Override
    protected String getHibernateDialect() {
        return "org.hibernate.dialect.H2Dialect";
    }
}
