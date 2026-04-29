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
    public void describeReadsPrettyWriterPropertiesThroughBean() {
        SqlPrettyWriter writer = new SqlPrettyWriter();
        writer.setIndentation(2);
        StringWriter settings = new StringWriter();

        writer.describe(new PrintWriter(settings), false);

        assertThat(settings.toString()).contains("indentation=2");
    }

    @Test
    public void setSettingsAppliesStringPropertiesThroughBean() {
        CustomPropertyPrettyWriter writer = new CustomPropertyPrettyWriter();
        Properties properties = new Properties();
        properties.setProperty("customOption", "configured");

        writer.setSettings(properties);

        assertThat(writer.getCustomOption()).isEqualTo("configured");
    }

    public static final class CustomPropertyPrettyWriter extends SqlPrettyWriter {
        private String customOption = "default";

        public String getCustomOption() {
            return customOption;
        }

        public Void setCustomOption(String customOption) {
            this.customOption = customOption;
            return null;
        }
    }
}
