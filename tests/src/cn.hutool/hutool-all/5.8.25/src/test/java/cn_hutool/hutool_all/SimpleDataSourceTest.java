/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.db.dialect.DriverNamePool;
import cn.hutool.db.ds.simple.SimpleDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleDataSourceTest {

    @Test
    void initLoadsConfiguredJdbcDriver() {
        SimpleDataSource dataSource = new SimpleDataSource(
                "jdbc:h2:mem:hutool_simple_datasource",
                "sa",
                "secret",
                DriverNamePool.DRIVER_H2);

        assertThat(dataSource.getDriver()).isEqualTo(DriverNamePool.DRIVER_H2);
        assertThat(dataSource.getUrl()).isEqualTo("jdbc:h2:mem:hutool_simple_datasource");
        assertThat(dataSource.getUser()).isEqualTo("sa");
        assertThat(dataSource.getPass()).isEqualTo("secret");
    }
}
