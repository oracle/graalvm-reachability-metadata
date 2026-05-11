/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.clickhouse_jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.clickhouse.jdbc.JdbcConfig;
import com.clickhouse.jdbc.JdbcTypeMapping;

@SuppressWarnings("deprecation")
public class JdbcConfigTest {
    @Test
    void loadsConfiguredDialectAndTypeMappings() {
        Properties properties = new Properties();
        properties.setProperty(JdbcConfig.PROP_DIALECT, CustomTypeMapping.class.getName());
        properties.setProperty(JdbcConfig.PROP_TYPE_MAP,
                "String=java.lang.String,UInt64=java.math.BigInteger");

        JdbcConfig config = new JdbcConfig(properties);

        assertThat(config.getDialect()).isExactlyInstanceOf(CustomTypeMapping.class);
        Map<String, Class<?>> typeMap = config.getTypeMap();
        assertThat(typeMap)
                .containsEntry("String", String.class)
                .containsEntry("UInt64", BigInteger.class);
    }

    public static final class CustomTypeMapping extends JdbcTypeMapping {
        public CustomTypeMapping() {
        }
    }
}
