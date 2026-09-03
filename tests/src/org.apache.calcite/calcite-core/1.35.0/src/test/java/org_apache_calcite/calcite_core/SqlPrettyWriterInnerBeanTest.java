/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.sql.pretty.SqlPrettyWriter;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlPrettyWriterInnerBeanTest {
    @Test
    void readsAndWritesBeanSettings() {
        CustomWriter customWriter = new CustomWriter();
        Properties properties = new Properties();
        properties.setProperty("flavor", "compact");
        customWriter.setSettings(properties);

        StringWriter description = new StringWriter();
        new SqlPrettyWriter(SqlPrettyWriter.config()).describe(new PrintWriter(description), false);

        assertThat(customWriter.getFlavor()).isEqualTo("compact");
        assertThat(description.toString()).startsWith("dialect=");
    }

    public static class CustomWriter extends SqlPrettyWriter {
        private String flavor = "default";

        public CustomWriter() {
            super(SqlPrettyWriter.config());
        }

        public Void setFlavor(String flavor) {
            this.flavor = flavor;
            return null;
        }

        public String getFlavor() {
            return flavor;
        }
    }
}
