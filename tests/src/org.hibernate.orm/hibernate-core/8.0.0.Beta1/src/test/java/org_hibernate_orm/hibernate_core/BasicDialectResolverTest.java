/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.sql.Connection;
import java.sql.DriverManager;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.jdbc.dialect.spi.BasicDialectResolver;
import org.hibernate.engine.jdbc.dialect.spi.DatabaseMetaDataDialectResolutionInfoAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicDialectResolverTest {

    @Test
    void resolverInstantiatesTheDialectForAMatchingDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(SessionFactoryTestSupport.newH2Url(), "sa", "")) {
            DatabaseMetaDataDialectResolutionInfoAdapter info =
                    new DatabaseMetaDataDialectResolutionInfoAdapter(connection.getMetaData());

            Dialect h2 = new BasicDialectResolver("H2", H2Dialect.class).resolveDialect(info);
            Dialect postgres = new BasicDialectResolver("PostgreSQL", PostgreSQLDialect.class).resolveDialect(info);

            assertThat(h2).isExactlyInstanceOf(H2Dialect.class);
            assertThat(postgres).isNull();
        }
    }
}
