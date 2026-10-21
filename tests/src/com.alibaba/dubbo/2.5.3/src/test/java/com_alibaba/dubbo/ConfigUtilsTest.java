/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.common.utils.ConfigUtils;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class ConfigUtilsTest {
    @Test
    void loadsSingleAndMergedClasspathProperties() {
        Properties single = ConfigUtils.loadProperties("dubbo-test.properties", false, false);
        Properties merged = ConfigUtils.loadProperties("dubbo-test.properties", true, false);

        assertThat(single.getProperty("dubbo.test.name")).isEqualTo("metadata");
        assertThat(merged.getProperty("dubbo.test.name")).isEqualTo("metadata");
    }
}
