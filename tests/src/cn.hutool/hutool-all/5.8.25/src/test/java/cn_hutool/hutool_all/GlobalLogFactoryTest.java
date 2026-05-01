/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.log.GlobalLogFactory;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import cn.hutool.log.dialect.console.ConsoleLogFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalLogFactoryTest {
    @Test
    public void installsLogFactoryFromFactoryClass() {
        LogFactory originalFactory = GlobalLogFactory.get();

        try {
            LogFactory configuredFactory = GlobalLogFactory.set(ConsoleLogFactory.class);

            assertThat(configuredFactory).isInstanceOf(ConsoleLogFactory.class);
            assertThat(GlobalLogFactory.get()).isSameAs(configuredFactory);
            assertThat(configuredFactory.getName()).isEqualTo("Hutool Console Logging");

            Log loggerByName = configuredFactory.getLog("coverage.global-log-factory");
            Log loggerByClass = configuredFactory.getLog(GlobalLogFactoryTest.class);

            assertThat(loggerByName).isNotNull();
            assertThat(loggerByClass).isNotNull();
        } finally {
            GlobalLogFactory.set(originalFactory);
        }
    }
}
