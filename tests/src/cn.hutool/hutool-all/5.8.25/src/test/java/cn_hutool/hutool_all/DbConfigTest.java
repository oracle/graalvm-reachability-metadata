/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.db.dialect.DriverNamePool;
import cn.hutool.db.ds.pooled.DbConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DbConfigTest {

    @Test
    void initIdentifiesAndLoadsH2Driver() {
        DbConfig config = new DbConfig();

        config.init("jdbc:h2:mem:hutool", "sa", "secret");

        assertThat(config.getUrl()).isEqualTo("jdbc:h2:mem:hutool");
        assertThat(config.getUser()).isEqualTo("sa");
        assertThat(config.getPass()).isEqualTo("secret");
        assertThat(config.getDriver()).isEqualTo(DriverNamePool.DRIVER_H2);
    }
}
