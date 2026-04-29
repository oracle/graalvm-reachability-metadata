/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;
import org.apache.calcite.sql.pretty.SqlPrettyWriter;
import org.junit.jupiter.api.Test;

public class SqlPrettyWriterInnerBeanTest {
    @Test
    @SuppressWarnings("deprecation")
    void settingsAndDescriptionUseBeanBackedProperties() {
        PropertyAwarePrettyWriter writer = new PropertyAwarePrettyWriter();
        Properties properties = new Properties();
        properties.setProperty("indentation", "6");

        writer.setSettings(properties);

        assertThat(writer.requestedIndentation()).isEqualTo("6");

        int defaultLineLength = writer.getLineLength();
        int configuredLineLength = defaultLineLength == 47 ? 48 : 47;
        writer.setLineLength(configuredLineLength);

        StringWriter description = new StringWriter();
        writer.describe(new PrintWriter(description), false);

        assertThat(description.toString())
                .contains("lineLength=" + configuredLineLength);
    }

    public static class PropertyAwarePrettyWriter extends SqlPrettyWriter {
        private String requestedIndentation;

        public PropertyAwarePrettyWriter() {
            super(SqlPrettyWriter.config());
        }

        public Void setIndentation(String indentation) {
            requestedIndentation = indentation;
            return null;
        }

        String requestedIndentation() {
            return requestedIndentation;
        }
    }
}
