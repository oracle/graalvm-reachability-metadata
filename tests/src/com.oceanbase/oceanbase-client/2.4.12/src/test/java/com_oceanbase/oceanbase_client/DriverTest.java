/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oceanbase.oceanbase_client;

import com.oceanbase.jdbc.Driver;
import org.junit.jupiter.api.Test;

import java.sql.DriverPropertyInfo;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class DriverTest {
    @Test
    void exposesConfiguredValuesAndDescriptionsToJdbcConfigurationTools() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", "property-user");
        properties.setProperty("connectTimeout", "20000");
        DriverPropertyInfo[] information = new Driver().getPropertyInfo(
                "jdbc:oceanbase://localhost/catalog?user=url-user&tcpNoDelay=false", properties);
        Map<String, DriverPropertyInfo> byName = Arrays.stream(information)
                .collect(Collectors.toMap(property -> property.name, Function.identity()));

        assertThat(byName.size()).isGreaterThan(50);
        assertThat(byName.get("user").value).isEqualTo("property-user");
        assertThat(byName.get("connectTimeout").value).isEqualTo("20000");
        assertThat(byName.get("tcpNoDelay").value).isEqualTo("false");
        assertThat(byName.get("socketTimeout").description).isNotBlank();
    }

    @Test
    void exposesPropertiesWithoutAConnectionUrl() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", "form-user");
        properties.setProperty("socketTimeout", "30000");
        DriverPropertyInfo[] information = new Driver().getPropertyInfo("", properties);

        assertThat(information).anySatisfy(property -> {
            assertThat(property.name).isEqualTo("user");
            assertThat(property.value).isEqualTo("form-user");
        }).anySatisfy(property -> {
            assertThat(property.name).isEqualTo("socketTimeout");
            assertThat(property.value).isEqualTo("30000");
        });
    }
}
