/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_jtds.jtds;

import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import net.sourceforge.jtds.jdbc.Driver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MessagesTest {
    @Test
    void describesConnectionPropertiesUsingTheDriverResourceBundle() throws Exception {
        Driver driver = new Driver();
        DriverPropertyInfo[] properties = driver.getPropertyInfo(
                "jdbc:jtds:sqlserver://localhost:1433/catalog", new Properties());
        Map<String, DriverPropertyInfo> byName = Arrays.stream(properties)
                .collect(Collectors.toMap(property -> property.name, property -> property));

        assertThat(byName).containsKeys("SERVERNAME", "PORTNUMBER", "DATABASENAME", "USER", "PASSWORD");
        assertThat(byName.get("SERVERNAME").value).isEqualTo("localhost");
        assertThat(byName.get("SERVERNAME").required).isTrue();
        assertThat(byName.get("SERVERNAME").description).isNotBlank();
        assertThat(byName.get("PORTNUMBER").value).isEqualTo("1433");
        assertThat(byName.get("DATABASENAME").value).isEqualTo("catalog");
    }

    @Test
    void formatsAnInvalidConnectionUrlInTheDiagnostic() {
        String url = "jdbc:jtds:unknown://localhost/catalog";
        SQLException exception = assertThrows(SQLException.class,
                () -> new Driver().getPropertyInfo(url, new Properties()));

        assertThat(exception.getSQLState()).isEqualTo("08001");
        assertThat(exception.getMessage()).contains(url);
    }
}
