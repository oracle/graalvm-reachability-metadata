/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_sql.micronaut_jdbc;

import io.micronaut.jdbc.BasicJdbcConfiguration;
import io.micronaut.jdbc.CalculatedSettings;

import java.util.HashMap;
import java.util.Map;

final class TestJdbcConfiguration implements BasicJdbcConfiguration {
    private final String name;
    private final Map<String, Object> properties = new HashMap<>();
    private final CalculatedSettings settings;

    TestJdbcConfiguration(String name) {
        this.name = name;
        this.settings = new CalculatedSettings(this);
    }

    CalculatedSettings getSettings() {
        return settings;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getConfiguredUrl() {
        return (String) properties.get("url");
    }

    @Override
    public String getUrl() {
        return settings.getUrl();
    }

    @Override
    public void setUrl(String url) {
        properties.put("url", url);
    }

    @Override
    public String getConfiguredDriverClassName() {
        return (String) properties.get("driverClassName");
    }

    @Override
    public String getDriverClassName() {
        return settings.getDriverClassName();
    }

    @Override
    public void setDriverClassName(String driverClassName) {
        properties.put("driverClassName", driverClassName);
    }

    @Override
    public String getConfiguredUsername() {
        return (String) properties.get("username");
    }

    @Override
    public String getUsername() {
        return settings.getUsername();
    }

    @Override
    public void setUsername(String username) {
        properties.put("username", username);
    }

    @Override
    public String getConfiguredPassword() {
        return (String) properties.get("password");
    }

    @Override
    public String getPassword() {
        return settings.getPassword();
    }

    @Override
    public void setPassword(String password) {
        properties.put("password", password);
    }

    @Override
    public String getConfiguredValidationQuery() {
        return (String) properties.get("validationQuery");
    }

    @Override
    public String getValidationQuery() {
        return settings.getValidationQuery();
    }

    void setValidationQuery(String validationQuery) {
        properties.put("validationQuery", validationQuery);
    }

    @Override
    public void setDataSourceProperties(Map<String, ?> dataSourceProperties) {
        properties.putAll(dataSourceProperties);
    }
}
