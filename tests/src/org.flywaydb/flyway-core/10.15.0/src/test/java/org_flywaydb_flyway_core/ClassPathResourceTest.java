/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb_flyway_core;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.flywaydb.core.internal.resource.classpath.ClassPathResource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPathResourceTest {

    @Test
    void locatesAndReadsPackagedMigration() throws IOException {
        ClassPathResource resource = new ClassPathResource(
                null,
                "db/migration/V1__create_table.sql",
                getClass().getClassLoader(),
                StandardCharsets.UTF_8);

        assertThat(resource.exists()).isTrue();

        StringWriter contents = new StringWriter();
        try (Reader reader = resource.read()) {
            reader.transferTo(contents);
        }

        assertThat(contents.toString())
                .contains("CREATE TABLE test")
                .contains("title VARCHAR NOT NULL");
    }
}
