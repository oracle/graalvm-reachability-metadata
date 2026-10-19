/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.common.logger.Level;
import com.alibaba.dubbo.common.logger.jdk.JdkLoggerAdapter;
import org.junit.jupiter.api.Test;

public class JdkLoggerAdapterTest {
    @Test
    void configuresAndCreatesJdkLoggers() {
        JdkLoggerAdapter adapter = new JdkLoggerAdapter();
        adapter.setLevel(Level.WARN);

        assertThat(adapter.getLevel()).isEqualTo(Level.WARN);
        assertThat(adapter.getLogger("dubbo-test")).isNotNull();
        assertThat(adapter.getLogger(JdkLoggerAdapterTest.class)).isNotNull();
    }
}
