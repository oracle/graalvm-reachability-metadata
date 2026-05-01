/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.db.ds.pooled.DbConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DbConfigTest {
    private static final String H2_DRIVER = "org.h2.Driver";
    private static final String H2_JDBC_URL = "jdbc:h2:mem:db_config_test";

    @Test
    public void initLoadsIdentifiedDriverClass() {
        DbConfig config = new DbConfig(H2_JDBC_URL, "sa", "");

        assertThat(config.getDriver()).isEqualTo(H2_DRIVER);
        assertThat(config.getUrl()).isEqualTo(H2_JDBC_URL);
        assertThat(config.getUser()).isEqualTo("sa");
        assertThat(config.getPass()).isEmpty();
    }
}
