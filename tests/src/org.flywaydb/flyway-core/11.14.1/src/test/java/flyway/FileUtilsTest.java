/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import org.flywaydb.core.internal.util.FileUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FileUtilsTest {

    @Test
    void readsClasspathResourceAsStringWithProvidedClassLoader() {
        String migration = FileUtils.readResourceAsString(
                getClass().getClassLoader(),
                "db/migration/V1__create_table.sql");

        assertThat(migration)
                .contains("CREATE TABLE test")
                .contains("title VARCHAR NOT NULL");
    }
}
