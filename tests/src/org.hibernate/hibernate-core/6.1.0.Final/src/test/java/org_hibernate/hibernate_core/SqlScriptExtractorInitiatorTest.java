/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.tool.schema.spi.SqlScriptCommandExtractor;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlScriptExtractorInitiatorTest {

    @Test
    public void instantiatesAndUsesTheConfiguredExtractor() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(
                        AvailableSettings.HBM2DDL_IMPORT_FILES_SQL_EXTRACTOR,
                        RecordingExtractor.class.getName()
                )
                .build();
        try {
            SqlScriptCommandExtractor extractor =
                    registry.getService(SqlScriptCommandExtractor.class);

            assertThat(extractor.extractCommands(new StringReader("select 1"), null))
                    .containsExactly("select 1");
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    public static class RecordingExtractor implements SqlScriptCommandExtractor {
        @Override
        public List<String> extractCommands(Reader reader, Dialect dialect) {
            return List.of("select 1");
        }
    }
}
