/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.engine.jdbc.dialect.spi.BasicDialectResolver;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicDialectResolverTest {

    @Test
    public void instantiatesTheDialectWhenDatabaseInformationMatches() {
        BasicDialectResolver resolver = new BasicDialectResolver("H2", H2Dialect.class);

        Dialect dialect = resolver.resolveDialect(new H2ResolutionInfo());

        assertThat(dialect).isInstanceOf(H2Dialect.class);
    }

    private static class H2ResolutionInfo implements DialectResolutionInfo {
        @Override
        public String getDatabaseName() {
            return "H2";
        }

        @Override
        public String getDatabaseVersion() {
            return "2.1";
        }

        @Override
        public int getDatabaseMajorVersion() {
            return 2;
        }

        @Override
        public int getDatabaseMinorVersion() {
            return 1;
        }

        @Override
        public String getDriverName() {
            return "H2 JDBC Driver";
        }

        @Override
        public int getDriverMajorVersion() {
            return 2;
        }

        @Override
        public int getDriverMinorVersion() {
            return 1;
        }

        @Override
        public String getSQLKeywords() {
            return "";
        }
    }
}
