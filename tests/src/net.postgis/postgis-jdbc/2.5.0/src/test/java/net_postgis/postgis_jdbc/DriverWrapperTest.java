/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_postgis.postgis_jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.postgis.DriverWrapper;

public class DriverWrapperTest {

    @Test
    void constructorLoadsTypesAdderAndAcceptsPostgisJdbcUrls() throws SQLException {
        DriverWrapper driver = new DriverWrapper();

        assertThat(driver.acceptsURL("jdbc:postgresql_postGIS://localhost:5432/spatial")).isTrue();
        assertThat(driver.acceptsURL("jdbc:postgresql://localhost:5432/spatial")).isFalse();
        assertThat(driver.acceptsURL("jdbc:postgresql_postGISX://localhost:5432/spatial")).isFalse();
    }

    @Test
    void staticTypeRegistrationLoadsTypesAdderBeforeUsingConnection() {
        assertThatThrownBy(() -> DriverWrapper.addGISTypes80(null))
                .isInstanceOf(NullPointerException.class);
    }
}
