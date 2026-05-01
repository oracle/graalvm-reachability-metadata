/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.db.ds.simple.SimpleDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleDataSourceTest {
    private static final String H2_DRIVER = "org.h2.Driver";
    private static final String H2_JDBC_URL = "jdbc:h2:mem:simple_data_source_test";

    @Test
    public void initInfersAndLoadsJdbcDriverClass() {
        SimpleDataSource dataSource = new SimpleDataSource(H2_JDBC_URL, "sa", "");

        assertThat(dataSource.getDriver()).isEqualTo(H2_DRIVER);
        assertThat(dataSource.getUrl()).isEqualTo(H2_JDBC_URL);
        assertThat(dataSource.getUser()).isEqualTo("sa");
        assertThat(dataSource.getPass()).isEmpty();
    }
}
