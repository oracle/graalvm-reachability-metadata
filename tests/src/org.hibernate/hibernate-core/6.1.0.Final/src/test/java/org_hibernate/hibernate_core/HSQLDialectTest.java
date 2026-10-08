/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.dialect.HSQLDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HSQLDialectTest {

    @Test
    public void reportsTheInstalledHsqlDatabaseVersion() {
        HSQLDialect dialect = new HSQLDialect();

        assertThat(dialect.getVersion()).isNotNull();
        assertThat(dialect.getDefaultStatementBatchSize()).isEqualTo(15);
    }
}
