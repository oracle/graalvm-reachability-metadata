/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.log.GlobalLogFactory;
import cn.hutool.log.LogFactory;
import cn.hutool.log.dialect.console.ConsoleLogFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalLogFactoryTest {

    @Test
    void setsGlobalFactoryFromFactoryClass() {
        LogFactory previousFactory = GlobalLogFactory.get();

        try {
            LogFactory selectedFactory = GlobalLogFactory.set(ConsoleLogFactory.class);

            assertThat(selectedFactory).isInstanceOf(ConsoleLogFactory.class);
            assertThat(GlobalLogFactory.get()).isSameAs(selectedFactory);
            assertThat(selectedFactory.getLog(GlobalLogFactoryTest.class).getName())
                    .isEqualTo(GlobalLogFactoryTest.class.getName());
        } finally {
            GlobalLogFactory.set(previousFactory);
        }
    }
}
