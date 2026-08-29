/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import org.jdbi.v3.core.locator.ClasspathSqlLocator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClasspathSqlLocatorTest {
    @Test
    void loadsSqlOwnedByTheTestType() {
        String sql = ClasspathSqlLocator.create().locate(ClasspathSqlLocatorTest.class, "selectGreeting");

        assertThat(sql.trim()).isEqualTo("select 'hello from classpath'");
    }
}
