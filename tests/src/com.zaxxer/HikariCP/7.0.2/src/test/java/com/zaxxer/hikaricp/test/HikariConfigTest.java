/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.zaxxer.hikaricp.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariCredentialsProvider;
import com.zaxxer.hikari.SQLExceptionOverride;
import com.zaxxer.hikari.util.Credentials;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class HikariConfigTest {
    @Test
    void readsTypedPropertiesAndCreatesConfiguredSupportObjects() {
        Properties properties = new Properties();
        properties.setProperty("jdbcUrl", "jdbc:recording:properties");
        properties.setProperty("username", "property-user");
        properties.setProperty("password", "property-password");
        properties.setProperty("maximumPoolSize", "3");
        properties.setProperty("minimumIdle", "1");
        properties.setProperty("connectionTimeout", "750");
        properties.setProperty("validationTimeout", "500");
        properties.setProperty("poolName", "properties-pool");
        properties.setProperty("exceptionOverrideClassName", TestSQLExceptionOverride.class.getName());
        properties.setProperty("credentialsProviderClassName", StaticCredentialsProvider.class.getName());

        HikariConfig config = new HikariConfig(properties);

        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:recording:properties");
        assertThat(config.getMaximumPoolSize()).isEqualTo(3);
        assertThat(config.getMinimumIdle()).isEqualTo(1);
        assertThat(config.getConnectionTimeout()).isEqualTo(750);
        assertThat(config.getValidationTimeout()).isEqualTo(500);
        assertThat(config.getPoolName()).isEqualTo("properties-pool");
        assertThat(config.getExceptionOverride()).isInstanceOf(TestSQLExceptionOverride.class);
        assertThat(config.getCredentialsProvider()).isInstanceOf(StaticCredentialsProvider.class);
        assertThat(config.getCredentialsProvider().getCredentials().getUsername()).isEqualTo("provider-user");
        assertThat(config.getExceptionOverride().adjudicate(new SQLException("test")))
                .isEqualTo(SQLExceptionOverride.Override.DO_NOT_EVICT);
    }

    public static final class StaticCredentialsProvider implements HikariCredentialsProvider {
        public StaticCredentialsProvider() {
        }

        @Override
        public Credentials getCredentials() {
            return Credentials.of("provider-user", "provider-password");
        }
    }

    public static final class TestSQLExceptionOverride implements SQLExceptionOverride {
        public TestSQLExceptionOverride() {
        }

        @Override
        public SQLExceptionOverride.Override adjudicate(SQLException sqlException) {
            return SQLExceptionOverride.Override.DO_NOT_EVICT;
        }
    }
}
