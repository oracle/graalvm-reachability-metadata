/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mysql;

import com.mysql.cj.jdbc.NonRegisteringDriver;
import org.junit.jupiter.api.Test;

import java.sql.DriverPropertyInfo;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class NonRegisteringDriverTest {
    @Test
    void propertyDiscoveryResolvesConnectionSettingsAndLocalizedDescriptions() throws Exception {
        NonRegisteringDriver driver = new NonRegisteringDriver();
        Properties properties = new Properties();
        properties.setProperty("user", "application-user");
        properties.setProperty("password", "application-secret");
        properties.setProperty("connectTimeout", "10000");
        DriverPropertyInfo[] discovered = driver.getPropertyInfo("jdbc:mysql://db.example:3307/catalog", properties);
        Map<String, DriverPropertyInfo> settings = Arrays.stream(discovered)
                .collect(Collectors.toMap(property -> property.name, property -> property, (first, duplicate) -> first));

        assertThat(settings.get("host").value).isEqualTo("db.example");
        assertThat(settings.get("port").value).isEqualTo("3307");
        assertThat(settings.get("dbname").value).isEqualTo("catalog");
        assertThat(settings.get("user").value).isEqualTo("application-user");
        assertThat(settings.get("password").value).isEqualTo("application-secret");
        assertThat(settings.get("connectTimeout").value).isEqualTo("10000");
        assertThat(settings.get("host").required).isTrue();
        assertThat(settings.get("port").required).isFalse();
        assertThat(settings.get("host").description).isEqualTo("Hostname of MySQL Server");
        assertThat(settings.get("user").description).isEqualTo("Username to authenticate as");
    }

    @Test
    void unrelatedJdbcProtocolIsDeclinedWithoutOpeningAConnection() throws Exception {
        NonRegisteringDriver driver = new NonRegisteringDriver();
        assertThat(driver.acceptsURL("jdbc:mysql://db.example/catalog")).isTrue();
        assertThat(driver.acceptsURL("jdbc:other://db.example/catalog")).isFalse();
        assertThat(driver.connect("jdbc:other://db.example/catalog", new Properties())).isNull();
    }
}
